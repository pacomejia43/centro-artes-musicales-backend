package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.Pago;
import com.centroartesmusicales.backend.model.StripePriceCache;
import com.centroartesmusicales.backend.model.TipoPrecioStripe;
import com.centroartesmusicales.backend.repository.StripePriceCacheRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Única capa que habla directamente con el SDK de Stripe. Nunca decide si un alumno puede pagar
 * ni cuánto debe pagar — eso lo deciden PagoService/StripeCheckoutService/SuscripcionService
 * consultando la base de datos; aquí solo se traduce esa decisión ya tomada a llamadas de Stripe.
 */
@Service
@RequiredArgsConstructor
public class StripeService {

    private final AppProperties appProperties;
    private final StripePriceCacheRepository stripePriceCacheRepository;

    private void verificarConfigurado() {
        if (!appProperties.stripe().configurado()) {
            throw new BusinessRuleException("Stripe no está configurado en el servidor.");
        }
    }

    public String publishableKey() {
        return appProperties.stripe().publishableKey();
    }

    /**
     * En este proyecto Usuario.email es en realidad un "ID de usuario" (ver el commit que renombró
     * la etiqueta en el panel de "Email" a "ID usuario") — muchos alumnos tienen ahí un valor como
     * "Valentina1", no un correo real. La API de Customer.create de Stripe valida el formato y
     * rechaza la solicitud completa si se le manda algo así, así que solo se incluye email cuando
     * de verdad tiene forma de correo; si no, Stripe Checkout simplemente lo pide en la propia
     * pantalla de pago.
     */
    private static final Pattern PATRON_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Sin efectos secundarios a propósito, para poder probarla sin llamar a la API real de Stripe. */
    static boolean pareceEmailValido(String valor) {
        return valor != null && PATRON_EMAIL.matcher(valor).matches();
    }

    /** Siempre crea un Customer nuevo — el llamador decide si ya había uno guardado. */
    public String crearCustomer(Alumno alumno) throws StripeException {
        verificarConfigurado();
        String posibleEmail = alumno.getUsuario().getEmail();
        CustomerCreateParams.Builder params = CustomerCreateParams.builder()
                .setName(alumno.getUsuario().getNombre())
                .putMetadata("student_id", alumno.getId().toString());
        if (pareceEmailValido(posibleEmail)) {
            params.setEmail(posibleEmail);
        }
        return Customer.create(params.build()).getId();
    }

    public String obtenerOCrearPriceUnico(BigDecimal monto) throws StripeException {
        return obtenerOCrearPrice(monto, TipoPrecioStripe.UNICO);
    }

    public String obtenerOCrearPriceRecurrente(BigDecimal monto) throws StripeException {
        return obtenerOCrearPrice(monto, TipoPrecioStripe.RECURRENTE);
    }

    /**
     * Cachea por (monto, tipo) en stripe_price_cache para no crear un Product/Price nuevo cada
     * vez que alguien paga el mismo importe (ver StripePriceCache). Ante una carrera muy poco
     * probable (dos alumnos pagando por primera vez el mismo monto en el mismo instante), el
     * unique constraint de la tabla puede rechazar el segundo insert; en ese caso se descarta el
     * Price recién creado en Stripe y se reutiliza el que sí quedó cacheado, para que de aquí en
     * adelante nunca haya dos precios activos para el mismo monto. (No hace falta @Transactional
     * aquí: saveAndFlush ya corre en su propia transacción vía Spring Data, y el resto de este
     * método son llamadas HTTP a Stripe que de todas formas no se pueden hacer atómicas con la BD.)
     */
    private String obtenerOCrearPrice(BigDecimal monto, TipoPrecioStripe tipo) throws StripeException {
        verificarConfigurado();
        Optional<StripePriceCache> existente = stripePriceCacheRepository.findByMontoAndTipo(monto, tipo);
        if (existente.isPresent()) {
            return existente.get().getStripePriceId();
        }

        long montoCentavos = monto.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        boolean recurrente = tipo == TipoPrecioStripe.RECURRENTE;

        Product product = Product.create(ProductCreateParams.builder()
                .setName("Mensualidad Centro de Artes Musicales — $" + monto.toPlainString() + " MXN"
                        + (recurrente ? " (pago automático)" : ""))
                .build());

        PriceCreateParams.Builder priceParams = PriceCreateParams.builder()
                .setProduct(product.getId())
                .setCurrency("mxn")
                .setUnitAmount(montoCentavos);
        if (recurrente) {
            priceParams.setRecurring(PriceCreateParams.Recurring.builder()
                    .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                    .build());
        }
        Price price = Price.create(priceParams.build());

        StripePriceCache cache = StripePriceCache.builder()
                .monto(monto)
                .tipo(tipo)
                .stripeProductId(product.getId())
                .stripePriceId(price.getId())
                .createdAt(LocalDateTime.now())
                .build();
        try {
            stripePriceCacheRepository.saveAndFlush(cache);
        } catch (DataIntegrityViolationException carrera) {
            return stripePriceCacheRepository.findByMontoAndTipo(monto, tipo)
                    .orElseThrow(() -> carrera)
                    .getStripePriceId();
        }
        return price.getId();
    }

    public Session crearCheckoutSessionPagoUnico(String customerId, String priceId, Alumno alumno, Pago pago) throws StripeException {
        verificarConfigurado();
        String successUrl = appProperties.frontendUrl()
                + "/mi-cuenta.html?pago=exito&pago_id=" + pago.getId() + "&session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = appProperties.frontendUrl() + "/mi-cuenta.html?pago=cancelado";

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomer(customerId)
                .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("student_id", alumno.getId().toString())
                .putMetadata("payment_id", pago.getId().toString())
                .putMetadata("concept_type", "MENSUALIDAD")
                // El metadata del Session no se copia solo al PaymentIntent — hay que repetirlo aquí
                // para poder identificar el pago desde eventos como payment_intent.payment_failed.
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("student_id", alumno.getId().toString())
                        .putMetadata("payment_id", pago.getId().toString())
                        .build())
                .build();
        return Session.create(params);
    }

    public Session crearCheckoutSessionSuscripcion(String customerId, String priceId, Alumno alumno) throws StripeException {
        verificarConfigurado();
        String successUrl = appProperties.frontendUrl() + "/mi-cuenta.html?suscripcion=exito&session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = appProperties.frontendUrl() + "/mi-cuenta.html?suscripcion=cancelada";

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("student_id", alumno.getId().toString())
                .putMetadata("concept_type", "MENSUALIDAD_RECURRENTE")
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("student_id", alumno.getId().toString())
                        .build())
                .build();
        return Session.create(params);
    }

    /** Cancela al final del periodo ya pagado, no de inmediato — el alumno conserva el ciclo que ya pagó. */
    public void cancelarSuscripcion(String stripeSubscriptionId) throws StripeException {
        verificarConfigurado();
        Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
        subscription.update(SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build());
    }

    public Event verificarYConstruirEvento(String payload, String firmaHeader) throws SignatureVerificationException {
        String webhookSecret = appProperties.stripe().webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new BusinessRuleException("STRIPE_WEBHOOK_SECRET no está configurado en el servidor.");
        }
        return Webhook.constructEvent(payload, firmaHeader, webhookSecret);
    }
}

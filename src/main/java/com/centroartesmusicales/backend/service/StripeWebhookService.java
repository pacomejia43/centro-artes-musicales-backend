package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.EstadoSuscripcion;
import com.centroartesmusicales.backend.model.Pago;
import com.centroartesmusicales.backend.model.StripeWebhookEvento;
import com.centroartesmusicales.backend.model.Suscripcion;
import com.centroartesmusicales.backend.repository.PagoTransaccionRepository;
import com.centroartesmusicales.backend.repository.StripeWebhookEventoRepository;
import com.centroartesmusicales.backend.repository.SuscripcionRepository;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * Punto único de entrada para todo evento de Stripe ya verificado (ver StripeService#verificarYConstruirEvento
 * y StripeWebhookController). Nunca confía en nada del payload sin haber pasado por esa
 * verificación de firma antes.
 *
 * Idempotencia: procesar() primero revisa stripe_webhook_evento por el id nativo del evento y
 * corta ahí si ya se procesó — cubre el caso normal de un reintento de entrega de Stripe, que
 * llega separado en el tiempo, no en paralelo. Cada manejador además vuelve a comprobar por su
 * cuenta (buscando por checkout_session_id / invoice_id antes de crear una transacción) para
 * cubrir también el caso, mucho más raro, de dos entregas del mismo evento llegando casi
 * simultáneamente — así no se necesita una segunda transacción de BD separada solo para blindar
 * la marca de "ya procesado" contra esa carrera.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeWebhookEventoRepository webhookEventoRepository;
    private final PagoTransaccionRepository pagoTransaccionRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PagoService pagoService;
    private final AlumnoService alumnoService;
    private final AppProperties appProperties;

    @Transactional
    public void procesar(Event event) {
        if (webhookEventoRepository.existsByStripeEventId(event.getId())) {
            log.info("Evento Stripe {} ({}) ya procesado, se ignora.", event.getId(), event.getType());
            return;
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> manejarCheckoutCompletado(event);
            case "payment_intent.payment_failed" -> manejarPagoFallido(event);
            case "customer.subscription.created", "customer.subscription.updated" -> manejarSuscripcionActualizada(event);
            case "customer.subscription.deleted" -> manejarSuscripcionCancelada(event);
            case "invoice.paid" -> manejarInvoicePagada(event);
            case "invoice.payment_failed" -> manejarInvoiceFallida(event);
            case "charge.refunded" -> manejarReembolso(event);
            default -> log.debug("Evento Stripe {} ({}) no requiere manejo, se descarta.", event.getId(), event.getType());
        }

        webhookEventoRepository.save(StripeWebhookEvento.builder()
                .stripeEventId(event.getId())
                .tipo(event.getType())
                .procesadoAt(LocalDateTime.now())
                .build());
    }

    // ---------------------------------------------------------------- pagos únicos

    private void manejarCheckoutCompletado(Event event) {
        Session session = deserializar(event, Session.class);
        if (!"payment".equals(session.getMode())) {
            return; // las de mode=subscription se confirman vía customer.subscription.created + invoice.paid
        }
        if (!"paid".equals(session.getPaymentStatus())) {
            return; // ej. método de pago asíncrono todavía sin liquidar
        }
        String pagoIdStr = metadata(session.getMetadata(), "payment_id");
        if (pagoIdStr == null) {
            log.warn("checkout.session.completed {} sin metadata payment_id, se ignora.", session.getId());
            return;
        }
        if (pagoTransaccionRepository.findByStripeCheckoutSessionId(session.getId()).isPresent()) {
            return;
        }

        BigDecimal monto = centavosAMonto(session.getAmountTotal());
        pagoService.registrarTransaccionStripe(Long.valueOf(pagoIdStr), new PagoService.DatosTransaccionStripe(
                monto, session.getPaymentIntent(), session.getId(), null, null));
    }

    private void manejarPagoFallido(Event event) {
        PaymentIntent paymentIntent = deserializar(event, PaymentIntent.class);
        String pagoIdStr = metadata(paymentIntent.getMetadata(), "payment_id");
        if (pagoIdStr == null) {
            return; // no es un intento de pago único nuestro (los de suscripción se ven en invoice.payment_failed)
        }
        BigDecimal monto = centavosAMonto(paymentIntent.getAmount());
        pagoService.registrarTransaccionFallida(Long.valueOf(pagoIdStr), new PagoService.DatosTransaccionStripe(
                monto, paymentIntent.getId(), null, null, null));
    }

    private void manejarReembolso(Event event) {
        Charge charge = deserializar(event, Charge.class);
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            return;
        }
        pagoService.marcarReembolsoPorPaymentIntent(paymentIntentId);
    }

    // ---------------------------------------------------------------- suscripciones

    private void manejarSuscripcionActualizada(Event event) {
        Subscription subscription = deserializar(event, Subscription.class);

        Suscripcion suscripcion = suscripcionRepository.findByStripeSubscriptionId(subscription.getId())
                .or(() -> resolverSuscripcionPorMetadata(subscription))
                .orElse(null);
        if (suscripcion == null) {
            log.warn("{} {} sin fila previa y sin student_id en metadata, se ignora.",
                    event.getType(), subscription.getId());
            return;
        }

        String priceId = null;
        BigDecimal monto = suscripcion.getMonto();
        if (subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
            var item = subscription.getItems().getData().get(0);
            priceId = item.getPrice().getId();
            monto = centavosAMonto(item.getPrice().getUnitAmount());
        }

        suscripcion.setStripeSubscriptionId(subscription.getId());
        if (priceId != null) {
            suscripcion.setStripePriceId(priceId);
        }
        suscripcion.setStripeCustomerId(subscription.getCustomer());
        suscripcion.setEstado(mapearEstadoSuscripcion(subscription.getStatus()));
        suscripcion.setCancelacionProgramada(Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
        if (monto != null) {
            suscripcion.setMonto(monto);
        }
        suscripcionRepository.save(suscripcion);
    }

    private void manejarSuscripcionCancelada(Event event) {
        Subscription subscription = deserializar(event, Subscription.class);
        suscripcionRepository.findByStripeSubscriptionId(subscription.getId()).ifPresent(suscripcion -> {
            suscripcion.setEstado(EstadoSuscripcion.CANCELADA);
            suscripcion.setCancelacionProgramada(false);
            suscripcion.setFechaCancelacion(LocalDate.now(ZoneId.of(appProperties.timezone())));
            suscripcionRepository.save(suscripcion);
        });
    }

    private Optional<Suscripcion> resolverSuscripcionPorMetadata(Subscription subscription) {
        String alumnoIdStr = metadata(subscription.getMetadata(), "student_id");
        if (alumnoIdStr == null) {
            return Optional.empty();
        }
        Long alumnoId = Long.valueOf(alumnoIdStr);
        return Optional.of(suscripcionRepository.findByAlumno_Id(alumnoId).orElseGet(() -> {
            Suscripcion nueva = new Suscripcion();
            nueva.setAlumno(alumnoService.obtenerPorId(alumnoId));
            return nueva;
        }));
    }

    private EstadoSuscripcion mapearEstadoSuscripcion(String stripeStatus) {
        return switch (stripeStatus) {
            case "active", "trialing" -> EstadoSuscripcion.ACTIVA;
            case "past_due", "unpaid" -> EstadoSuscripcion.PAST_DUE;
            case "canceled", "incomplete_expired" -> EstadoSuscripcion.CANCELADA;
            default -> EstadoSuscripcion.INCOMPLETA; // "incomplete" y cualquier status futuro desconocido
        };
    }

    // ---------------------------------------------------------------- cobros recurrentes (invoices)

    private void manejarInvoicePagada(Event event) {
        Invoice invoice = deserializar(event, Invoice.class);
        String stripeSubscriptionId = subscriptionIdDeInvoice(invoice);
        if (stripeSubscriptionId == null) {
            return; // invoice de un cobro único, ya cubierto por checkout.session.completed
        }
        if (pagoTransaccionRepository.findByStripeInvoiceId(invoice.getId()).isPresent()) {
            return;
        }
        Long alumnoId = resolverAlumnoIdDeInvoice(invoice, stripeSubscriptionId);
        if (alumnoId == null) {
            log.warn("invoice.paid {} sin alumno resoluble, se ignora.", invoice.getId());
            return;
        }

        BigDecimal monto = centavosAMonto(invoice.getAmountPaid());
        YearMonth periodo = periodoDeInvoice(invoice);
        Pago pago = pagoService.crearOEncontrarCargoParaPeriodo(alumnoId, periodo, monto);
        pagoService.registrarTransaccionStripe(pago.getId(), new PagoService.DatosTransaccionStripe(
                monto, null, null, invoice.getId(), stripeSubscriptionId));
    }

    private void manejarInvoiceFallida(Event event) {
        Invoice invoice = deserializar(event, Invoice.class);
        String stripeSubscriptionId = subscriptionIdDeInvoice(invoice);
        if (stripeSubscriptionId == null) {
            return;
        }
        if (pagoTransaccionRepository.findByStripeInvoiceId(invoice.getId()).isPresent()) {
            return;
        }
        Long alumnoId = resolverAlumnoIdDeInvoice(invoice, stripeSubscriptionId);
        if (alumnoId == null) {
            log.warn("invoice.payment_failed {} sin alumno resoluble, se ignora.", invoice.getId());
            return;
        }

        BigDecimal monto = centavosAMonto(invoice.getAmountDue());
        YearMonth periodo = periodoDeInvoice(invoice);
        Pago pago = pagoService.crearOEncontrarCargoParaPeriodo(alumnoId, periodo, monto);
        pagoService.registrarTransaccionFallida(pago.getId(), new PagoService.DatosTransaccionStripe(
                monto, null, null, invoice.getId(), stripeSubscriptionId));
    }

    private String subscriptionIdDeInvoice(Invoice invoice) {
        Invoice.Parent parent = invoice.getParent();
        if (parent == null || parent.getSubscriptionDetails() == null) {
            return null;
        }
        return parent.getSubscriptionDetails().getSubscription();
    }

    /** Prioriza nuestra propia tabla suscripcion; si invoice.paid llegó antes que
     *  customer.subscription.created (Stripe no garantiza el orden de entrega), cae al
     *  stripe_customer_id del alumno, que ya existe desde que se creó el Customer al iniciar la
     *  activación (ver SuscripcionService#obtenerOCrearCustomerId). */
    private Long resolverAlumnoIdDeInvoice(Invoice invoice, String stripeSubscriptionId) {
        Optional<Long> porSuscripcion = suscripcionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .map(s -> s.getAlumno().getId());
        if (porSuscripcion.isPresent()) {
            return porSuscripcion.get();
        }
        String customerId = invoice.getCustomer();
        if (customerId == null) {
            return null;
        }
        return alumnoService.obtenerPorStripeCustomerId(customerId).map(Alumno::getId).orElse(null);
    }

    private YearMonth periodoDeInvoice(Invoice invoice) {
        return YearMonth.from(Instant.ofEpochSecond(invoice.getCreated()).atZone(ZoneId.of(appProperties.timezone())));
    }

    // ---------------------------------------------------------------- utilidades

    private <T extends StripeObject> T deserializar(Event event, Class<T> tipo) {
        StripeObject objeto = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new BusinessRuleException(
                        "No se pudo deserializar el evento Stripe " + event.getId()
                                + " (posible diferencia de versión de API)."));
        return tipo.cast(objeto);
    }

    private String metadata(Map<String, String> metadata, String clave) {
        return metadata != null ? metadata.get(clave) : null;
    }

    private BigDecimal centavosAMonto(Long centavos) {
        return centavos != null ? BigDecimal.valueOf(centavos, 2) : BigDecimal.ZERO;
    }
}

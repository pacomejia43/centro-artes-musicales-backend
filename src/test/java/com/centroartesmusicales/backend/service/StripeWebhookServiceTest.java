package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.EstadoSuscripcion;
import com.centroartesmusicales.backend.model.Pago;
import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Suscripcion;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.PagoTransaccionRepository;
import com.centroartesmusicales.backend.repository.StripeWebhookEventoRepository;
import com.centroartesmusicales.backend.repository.SuscripcionRepository;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Construye cada Event deserializando un JSON con la forma real de un webhook de Stripe (vía el
 * mismo Gson que usa el SDK, com.stripe.net.ApiResource.GSON) en vez de armar los objetos del
 * modelo a mano — así los tests ejercitan la misma ruta de deserialización que un webhook real,
 * incluido el enrutamiento por "type" que hace EventDataObjectDeserializer.
 */
@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private StripeWebhookEventoRepository webhookEventoRepository;
    @Mock
    private PagoTransaccionRepository pagoTransaccionRepository;
    @Mock
    private SuscripcionRepository suscripcionRepository;
    @Mock
    private PagoService pagoService;
    @Mock
    private AlumnoService alumnoService;

    private StripeWebhookService stripeWebhookService;
    private Alumno alumno;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("unit-test-secret-unit-test-secret-32b", 3_600_000L),
                new AppProperties.Cors(List.of("http://localhost:3000")),
                new AppProperties.Admin(new AppProperties.Admin.Bootstrap("", "", "Administrador")),
                "America/Mexico_City",
                new AppProperties.Clases(4, 4, 60),
                new AppProperties.Pagos(new BigDecimal("600.00")),
                new AppProperties.Stripe("sk_test_x", "pk_test_x", "whsec_x"),
                "http://localhost:5500"
        );
        stripeWebhookService = new StripeWebhookService(
                webhookEventoRepository, pagoTransaccionRepository, suscripcionRepository,
                pagoService, alumnoService, appProperties);

        Usuario usuario = Usuario.builder().id(1L).email("valentina@test.com").nombre("Valentina")
                .password("hash").role(Role.ALUMNO).enabled(true).build();
        alumno = Alumno.builder().id(10L).usuario(usuario).fechaInscripcion(LocalDate.now()).activo(true).build();

        lenient().when(webhookEventoRepository.existsByStripeEventId(any())).thenReturn(false);
        lenient().when(webhookEventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Event evento(String id, String tipo, String objetoJson) {
        // api_version es obligatorio: EventDataObjectDeserializer.getObject() hace NPE si falta
        // (compara contra la versión compilada en el SDK para decidir si puede deserializar).
        String envoltura = """
                {"id":"%s","type":"%s","api_version":"%s","data":{"object":%s}}
                """.formatted(id, tipo, com.stripe.Stripe.API_VERSION, objetoJson);
        return ApiResource.GSON.fromJson(envoltura, Event.class);
    }

    // ---------------------------------------------------------------- idempotencia

    @Test
    void procesar_eventoYaProcesado_noHaceNada() {
        when(webhookEventoRepository.existsByStripeEventId("evt_1")).thenReturn(true);
        Event event = evento("evt_1", "checkout.session.completed",
                """
                {"id":"cs_1","object":"checkout.session","mode":"payment","payment_status":"paid","payment_intent":"pi_1","amount_total":60000,"metadata":{"payment_id":"50"}}
                """);

        stripeWebhookService.procesar(event);

        verify(pagoService, never()).registrarTransaccionStripe(anyLong(), any());
        verify(webhookEventoRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- checkout.session.completed

    @Test
    void checkoutCompletado_registraTransaccionConfirmadaConElMontoDeStripe() {
        Event event = evento("evt_2", "checkout.session.completed",
                """
                {"id":"cs_123","object":"checkout.session","mode":"payment","payment_status":"paid","payment_intent":"pi_123","amount_total":60000,"metadata":{"payment_id":"50","student_id":"10"}}
                """);

        stripeWebhookService.procesar(event);

        ArgumentCaptor<PagoService.DatosTransaccionStripe> captor = ArgumentCaptor.forClass(PagoService.DatosTransaccionStripe.class);
        verify(pagoService).registrarTransaccionStripe(eq(50L), captor.capture());
        assertThat(captor.getValue().monto()).isEqualByComparingTo("600.00");
        assertThat(captor.getValue().stripePaymentIntentId()).isEqualTo("pi_123");
        assertThat(captor.getValue().stripeCheckoutSessionId()).isEqualTo("cs_123");
        verify(webhookEventoRepository).save(any());
    }

    @Test
    void checkoutCompletado_ignoraSesionesDeSuscripcion() {
        Event event = evento("evt_3", "checkout.session.completed",
                """
                {"id":"cs_999","object":"checkout.session","mode":"subscription","payment_status":"paid","metadata":{"student_id":"10"}}
                """);

        stripeWebhookService.procesar(event);

        verify(pagoService, never()).registrarTransaccionStripe(anyLong(), any());
    }

    @Test
    void checkoutCompletado_yaRegistrada_noDuplicaLaTransaccion() {
        when(pagoTransaccionRepository.findByStripeCheckoutSessionId("cs_123"))
                .thenReturn(Optional.of(new com.centroartesmusicales.backend.model.PagoTransaccion()));
        Event event = evento("evt_4", "checkout.session.completed",
                """
                {"id":"cs_123","object":"checkout.session","mode":"payment","payment_status":"paid","payment_intent":"pi_123","amount_total":60000,"metadata":{"payment_id":"50"}}
                """);

        stripeWebhookService.procesar(event);

        verify(pagoService, never()).registrarTransaccionStripe(anyLong(), any());
        // El evento en sí no se había procesado todavía, así que sigue marcándose procesado.
        verify(webhookEventoRepository).save(any());
    }

    // ---------------------------------------------------------------- payment_intent.payment_failed

    @Test
    void pagoFallido_registraTransaccionFallidaSinMoverElSaldo() {
        Event event = evento("evt_5", "payment_intent.payment_failed",
                """
                {"id":"pi_500","object":"payment_intent","amount":60000,"metadata":{"payment_id":"50"}}
                """);

        stripeWebhookService.procesar(event);

        ArgumentCaptor<PagoService.DatosTransaccionStripe> captor = ArgumentCaptor.forClass(PagoService.DatosTransaccionStripe.class);
        verify(pagoService).registrarTransaccionFallida(eq(50L), captor.capture());
        assertThat(captor.getValue().stripePaymentIntentId()).isEqualTo("pi_500");
        verify(pagoService, never()).registrarTransaccionStripe(anyLong(), any());
    }

    // ---------------------------------------------------------------- suscripciones

    @Test
    void suscripcionCreada_guardaLaFilaConLosDatosDeStripe() {
        when(suscripcionRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.empty());
        when(suscripcionRepository.findByAlumno_Id(10L)).thenReturn(Optional.empty());
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);

        Event event = evento("evt_6", "customer.subscription.created",
                """
                {"id":"sub_1","object":"subscription","status":"active","customer":"cus_1","cancel_at_period_end":false,
                 "metadata":{"student_id":"10"},
                 "items":{"object":"list","data":[{"id":"si_1","object":"subscription_item","price":{"id":"price_1","object":"price","unit_amount":60000}}]}}
                """);

        stripeWebhookService.procesar(event);

        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository).save(captor.capture());
        Suscripcion guardada = captor.getValue();
        assertThat(guardada.getStripeSubscriptionId()).isEqualTo("sub_1");
        assertThat(guardada.getStripePriceId()).isEqualTo("price_1");
        assertThat(guardada.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(guardada.getMonto()).isEqualByComparingTo("600.00");
        assertThat(guardada.getAlumno()).isEqualTo(alumno);
    }

    @Test
    void suscripcionCancelada_marcaCanceladaYFechaDeCancelacion() {
        Suscripcion existente = Suscripcion.builder().id(1L).alumno(alumno).stripeSubscriptionId("sub_1")
                .stripePriceId("price_1").stripeCustomerId("cus_1").estado(EstadoSuscripcion.ACTIVA)
                .monto(new BigDecimal("600.00")).cancelacionProgramada(true).build();
        when(suscripcionRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(existente));

        Event event = evento("evt_7", "customer.subscription.deleted",
                """
                {"id":"sub_1","object":"subscription","status":"canceled","customer":"cus_1"}
                """);

        stripeWebhookService.procesar(event);

        assertThat(existente.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
        assertThat(existente.getFechaCancelacion()).isEqualTo(LocalDate.now(java.time.ZoneId.of("America/Mexico_City")));
        verify(suscripcionRepository).save(existente);
    }

    // ---------------------------------------------------------------- cobros recurrentes (invoices)

    @Test
    void invoicePagada_creaElCargoDelPeriodoYLoRegistraComoConfirmado() {
        Suscripcion suscripcion = Suscripcion.builder().id(1L).alumno(alumno).stripeSubscriptionId("sub_1")
                .stripePriceId("price_1").stripeCustomerId("cus_1").estado(EstadoSuscripcion.ACTIVA)
                .monto(new BigDecimal("600.00")).build();
        when(suscripcionRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(suscripcion));
        Pago pagoDelPeriodo = Pago.builder().id(77L).alumno(alumno).monto(new BigDecimal("600.00"))
                .periodo(YearMonth.now()).fechaLimite(LocalDate.now()).build();
        when(pagoService.crearOEncontrarCargoParaPeriodo(eq(10L), any(), any())).thenReturn(pagoDelPeriodo);

        long ahoraEpoch = java.time.Instant.now().getEpochSecond();
        Event event = evento("evt_8", "invoice.paid",
                """
                {"id":"in_1","object":"invoice","customer":"cus_1","amount_paid":60000,"amount_due":60000,"created":%d,
                 "parent":{"type":"subscription_details","subscription_details":{"subscription":"sub_1"}}}
                """.formatted(ahoraEpoch));

        stripeWebhookService.procesar(event);

        verify(pagoService).crearOEncontrarCargoParaPeriodo(eq(10L), eq(YearMonth.now()), eq(new BigDecimal("600.00")));
        ArgumentCaptor<PagoService.DatosTransaccionStripe> captor = ArgumentCaptor.forClass(PagoService.DatosTransaccionStripe.class);
        verify(pagoService).registrarTransaccionStripe(eq(77L), captor.capture());
        assertThat(captor.getValue().stripeInvoiceId()).isEqualTo("in_1");
        assertThat(captor.getValue().stripeSubscriptionId()).isEqualTo("sub_1");
    }

    @Test
    void invoicePagada_sinSuscripcionEnBd_resuelveElAlumnoPorStripeCustomerId() {
        when(suscripcionRepository.findByStripeSubscriptionId("sub_2")).thenReturn(Optional.empty());
        when(alumnoService.obtenerPorStripeCustomerId("cus_2")).thenReturn(Optional.of(alumno));
        Pago pagoDelPeriodo = Pago.builder().id(78L).alumno(alumno).monto(new BigDecimal("600.00"))
                .periodo(YearMonth.now()).fechaLimite(LocalDate.now()).build();
        when(pagoService.crearOEncontrarCargoParaPeriodo(eq(10L), any(), any())).thenReturn(pagoDelPeriodo);

        long ahoraEpoch = java.time.Instant.now().getEpochSecond();
        Event event = evento("evt_9", "invoice.paid",
                """
                {"id":"in_2","object":"invoice","customer":"cus_2","amount_paid":60000,"amount_due":60000,"created":%d,
                 "parent":{"type":"subscription_details","subscription_details":{"subscription":"sub_2"}}}
                """.formatted(ahoraEpoch));

        stripeWebhookService.procesar(event);

        verify(pagoService).registrarTransaccionStripe(eq(78L), any());
    }

    @Test
    void invoiceFallida_dejaConstanciaSinCrearTransaccionConfirmada() {
        Suscripcion suscripcion = Suscripcion.builder().id(1L).alumno(alumno).stripeSubscriptionId("sub_1")
                .stripePriceId("price_1").stripeCustomerId("cus_1").estado(EstadoSuscripcion.ACTIVA)
                .monto(new BigDecimal("600.00")).build();
        when(suscripcionRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(suscripcion));
        Pago pagoDelPeriodo = Pago.builder().id(79L).alumno(alumno).monto(new BigDecimal("600.00"))
                .periodo(YearMonth.now()).fechaLimite(LocalDate.now()).build();
        when(pagoService.crearOEncontrarCargoParaPeriodo(eq(10L), any(), any())).thenReturn(pagoDelPeriodo);

        long ahoraEpoch = java.time.Instant.now().getEpochSecond();
        Event event = evento("evt_10", "invoice.payment_failed",
                """
                {"id":"in_3","object":"invoice","customer":"cus_1","amount_paid":0,"amount_due":60000,"created":%d,
                 "parent":{"type":"subscription_details","subscription_details":{"subscription":"sub_1"}}}
                """.formatted(ahoraEpoch));

        stripeWebhookService.procesar(event);

        verify(pagoService).registrarTransaccionFallida(eq(79L), any());
        verify(pagoService, never()).registrarTransaccionStripe(anyLong(), any());
    }

    // ---------------------------------------------------------------- reembolsos

    @Test
    void reembolso_marcaLaTransaccionOriginalReembolsada() {
        Event event = evento("evt_11", "charge.refunded",
                """
                {"id":"ch_1","object":"charge","payment_intent":"pi_123"}
                """);

        stripeWebhookService.procesar(event);

        verify(pagoService).marcarReembolsoPorPaymentIntent("pi_123");
    }
}

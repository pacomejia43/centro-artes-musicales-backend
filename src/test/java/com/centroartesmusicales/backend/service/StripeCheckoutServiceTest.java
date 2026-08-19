package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.Pago;
import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Usuario;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeCheckoutServiceTest {

    @Mock
    private PagoService pagoService;
    @Mock
    private AlumnoService alumnoService;
    @Mock
    private StripeService stripeService;

    private StripeCheckoutService stripeCheckoutService;
    private Alumno alumno;
    private Pago pago;

    @BeforeEach
    void setUp() {
        stripeCheckoutService = new StripeCheckoutService(pagoService, alumnoService, stripeService);

        Usuario usuario = Usuario.builder().id(1L).email("valentina@test.com").nombre("Valentina")
                .password("hash").role(Role.ALUMNO).enabled(true).build();
        alumno = Alumno.builder().id(10L).usuario(usuario).fechaInscripcion(LocalDate.now()).activo(true).build();
        pago = Pago.builder().id(50L).alumno(alumno).monto(new BigDecimal("600.00"))
                .periodo(YearMonth.now()).fechaLimite(LocalDate.now().plusDays(5)).build();
    }

    @Test
    void crearCheckout_usaElSaldoPendienteRealDeLaBdComoMonto() throws Exception {
        when(pagoService.obtenerPropio(1L, 50L)).thenReturn(pago);
        when(pagoService.montoPagado(pago)).thenReturn(new BigDecimal("100.00"));
        alumno.setStripeCustomerId("cus_existente");
        when(stripeService.obtenerOCrearPriceUnico(new BigDecimal("500.00"))).thenReturn("price_500");
        when(stripeService.crearCheckoutSessionPagoUnico(eq("cus_existente"), eq("price_500"), eq(alumno), eq(pago)))
                .thenReturn(sesionFalsa("https://checkout.stripe.com/x"));

        Session session = stripeCheckoutService.crearCheckoutParaCargo(1L, 50L);

        assertThat(session.getUrl()).isEqualTo("https://checkout.stripe.com/x");
        // El saldo pendiente (600 - 100 = 500), no el monto total ni nada mandado por el cliente.
        verify(stripeService).obtenerOCrearPriceUnico(new BigDecimal("500.00"));
        verify(alumnoService, never()).guardarStripeCustomerId(any(), any());
    }

    @Test
    void crearCheckout_sinCustomerPrevio_creaUnoYLoPersiste() throws Exception {
        when(pagoService.obtenerPropio(1L, 50L)).thenReturn(pago);
        when(pagoService.montoPagado(pago)).thenReturn(BigDecimal.ZERO);
        when(stripeService.crearCustomer(alumno)).thenReturn("cus_nuevo");
        when(stripeService.obtenerOCrearPriceUnico(any())).thenReturn("price_600");
        when(stripeService.crearCheckoutSessionPagoUnico(eq("cus_nuevo"), any(), any(), any()))
                .thenReturn(sesionFalsa("https://checkout.stripe.com/y"));

        stripeCheckoutService.crearCheckoutParaCargo(1L, 50L);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(alumnoService).guardarStripeCustomerId(eq(10L), captor.capture());
        assertThat(captor.getValue()).isEqualTo("cus_nuevo");
    }

    @Test
    void crearCheckout_cargoYaPagado_falla() {
        when(pagoService.obtenerPropio(1L, 50L)).thenReturn(pago);
        when(pagoService.montoPagado(pago)).thenReturn(new BigDecimal("600.00"));

        assertThatThrownBy(() -> stripeCheckoutService.crearCheckoutParaCargo(1L, 50L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void crearCheckout_alumnoIntentaPagarCargoDeOtro_falla() {
        // obtenerPropio ya valida pertenencia (ver PagoServiceTest) — aquí solo confirmamos que
        // StripeCheckoutService no atrapa esa excepción ni sigue adelante.
        when(pagoService.obtenerPropio(1L, 999L))
                .thenThrow(new ResourceNotFoundException("Pago no encontrado: 999"));

        assertThatThrownBy(() -> stripeCheckoutService.crearCheckoutParaCargo(1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Session sesionFalsa(String url) {
        Session session = new Session();
        session.setUrl(url);
        return session;
    }
}

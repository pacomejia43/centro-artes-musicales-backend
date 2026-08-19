package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.EstadoSuscripcion;
import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Suscripcion;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.SuscripcionRepository;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuscripcionServiceTest {

    @Mock
    private SuscripcionRepository suscripcionRepository;
    @Mock
    private AlumnoService alumnoService;
    @Mock
    private PagoService pagoService;
    @Mock
    private StripeService stripeService;

    private SuscripcionService suscripcionService;
    private Alumno alumno;

    @BeforeEach
    void setUp() {
        suscripcionService = new SuscripcionService(suscripcionRepository, alumnoService, pagoService, stripeService);
        Usuario usuario = Usuario.builder().id(1L).email("valentina@test.com").nombre("Valentina")
                .password("hash").role(Role.ALUMNO).enabled(true).build();
        alumno = Alumno.builder().id(10L).usuario(usuario).fechaInscripcion(LocalDate.now()).activo(true).build();
    }

    @Test
    void iniciarActivacion_creaSesionDeCheckoutConElPrecioRecurrenteDelAlumno() throws Exception {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        when(suscripcionRepository.findByAlumno_Id(10L)).thenReturn(Optional.empty());
        when(stripeService.crearCustomer(alumno)).thenReturn("cus_1");
        when(pagoService.montoMensual(alumno)).thenReturn(new BigDecimal("600.00"));
        when(stripeService.obtenerOCrearPriceRecurrente(new BigDecimal("600.00"))).thenReturn("price_rec_600");
        Session sesionFalsa = new Session();
        sesionFalsa.setUrl("https://checkout.stripe.com/sub");
        when(stripeService.crearCheckoutSessionSuscripcion("cus_1", "price_rec_600", alumno)).thenReturn(sesionFalsa);

        Session resultado = suscripcionService.iniciarActivacion(1L);

        assertThat(resultado.getUrl()).isEqualTo("https://checkout.stripe.com/sub");
        verify(alumnoService).guardarStripeCustomerId(10L, "cus_1");
    }

    @Test
    void iniciarActivacion_yaTieneUnaActivaSinCancelacionProgramada_falla() {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        Suscripcion activa = Suscripcion.builder().id(1L).alumno(alumno).estado(EstadoSuscripcion.ACTIVA)
                .cancelacionProgramada(false).monto(new BigDecimal("600.00"))
                .stripeSubscriptionId("sub_1").stripePriceId("price_1").stripeCustomerId("cus_1").build();
        when(suscripcionRepository.findByAlumno_Id(10L)).thenReturn(Optional.of(activa));

        assertThatThrownBy(() -> suscripcionService.iniciarActivacion(1L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancelar_programaLaCancelacionYLlamaAStripe() throws Exception {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        Suscripcion activa = Suscripcion.builder().id(1L).alumno(alumno).estado(EstadoSuscripcion.ACTIVA)
                .cancelacionProgramada(false).monto(new BigDecimal("600.00"))
                .stripeSubscriptionId("sub_1").stripePriceId("price_1").stripeCustomerId("cus_1").build();
        when(suscripcionRepository.findByAlumno_Id(10L)).thenReturn(Optional.of(activa));

        suscripcionService.cancelar(1L);

        verify(stripeService).cancelarSuscripcion("sub_1");
        assertThat(activa.isCancelacionProgramada()).isTrue();
        verify(suscripcionRepository).save(activa);
    }

    @Test
    void cancelar_sinSuscripcion_falla() throws Exception {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        when(suscripcionRepository.findByAlumno_Id(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> suscripcionService.cancelar(1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(stripeService, never()).cancelarSuscripcion(any());
    }

    @Test
    void cancelar_yaCancelada_falla() throws Exception {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        Suscripcion cancelada = Suscripcion.builder().id(1L).alumno(alumno).estado(EstadoSuscripcion.CANCELADA)
                .monto(new BigDecimal("600.00")).stripeSubscriptionId("sub_1").stripePriceId("price_1")
                .stripeCustomerId("cus_1").build();
        when(suscripcionRepository.findByAlumno_Id(10L)).thenReturn(Optional.of(cancelada));

        assertThatThrownBy(() -> suscripcionService.cancelar(1L))
                .isInstanceOf(BusinessRuleException.class);
        verify(stripeService, never()).cancelarSuscripcion(any());
    }

    @Test
    void cancelar_otroAlumnoNoPuedeCancelarUnaSuscripcionQueNoEsSuya() {
        // cancelar() siempre resuelve la suscripción a través del alumno autenticado
        // (alumnoService.obtenerPorUsuarioId + findByAlumno_Id), nunca por un id de suscripción
        // que pudiera mandar el cliente — así que no hay forma de apuntar a la de otro alumno.
        Usuario otroUsuario = Usuario.builder().id(2L).email("otro@test.com").nombre("Otro")
                .password("hash").role(Role.ALUMNO).enabled(true).build();
        Alumno otroAlumno = Alumno.builder().id(11L).usuario(otroUsuario).fechaInscripcion(LocalDate.now()).activo(true).build();
        when(alumnoService.obtenerPorUsuarioId(2L)).thenReturn(otroAlumno);
        when(suscripcionRepository.findByAlumno_Id(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> suscripcionService.cancelar(2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

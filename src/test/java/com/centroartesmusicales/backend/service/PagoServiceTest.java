package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.dto.pago.ActualizarPagoRequest;
import com.centroartesmusicales.backend.dto.pago.CrearPagoRequest;
import com.centroartesmusicales.backend.dto.pago.RegistrarTransaccionRequest;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.EstadoPago;
import com.centroartesmusicales.backend.model.EstadoTransaccion;
import com.centroartesmusicales.backend.model.MetodoPago;
import com.centroartesmusicales.backend.model.Pago;
import com.centroartesmusicales.backend.model.PagoTransaccion;
import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.PagoRepository;
import com.centroartesmusicales.backend.repository.PagoTransaccionRepository;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private PagoTransaccionRepository pagoTransaccionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AlumnoService alumnoService;

    private PagoService pagoService;
    private Alumno alumno;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("unit-test-secret-unit-test-secret-32b", 3_600_000L),
                new AppProperties.Cors(List.of("http://localhost:3000")),
                new AppProperties.Admin(new AppProperties.Admin.Bootstrap("", "", "Administrador")),
                "America/Mexico_City",
                new AppProperties.Clases(4, 4, 60),
                new AppProperties.Pagos(new BigDecimal("600.00"))
        );

        pagoService = new PagoService(pagoRepository, pagoTransaccionRepository, usuarioRepository, alumnoService, appProperties);

        Usuario usuario = Usuario.builder().id(1L).email("valentina@test.com").nombre("Valentina")
                .password("hash").role(Role.ALUMNO).enabled(true).build();
        alumno = Alumno.builder().id(10L).usuario(usuario).fechaInscripcion(LocalDate.now()).activo(true).build();

        lenient().when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pagoTransaccionRepository.save(any(PagoTransaccion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pago pagoDe600(EstadoPago estado) {
        return Pago.builder().id(50L).alumno(alumno).monto(new BigDecimal("600.00"))
                .periodo(YearMonth.now()).fechaLimite(LocalDate.now().plusDays(5)).estado(estado).build();
    }

    // ---------------------------------------------------------------- crearCargo

    @Test
    void crearCargo_exitoso() {
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(pagoRepository.existsByAlumno_IdAndPeriodo(eq(10L), any())).thenReturn(false);

        var request = new CrearPagoRequest(new BigDecimal("600.00"), YearMonth.now(), LocalDate.now().plusDays(10), null);
        Pago resultado = pagoService.crearCargo(10L, request);

        assertThat(resultado.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(resultado.getMonto()).isEqualByComparingTo("600.00");
    }

    @Test
    void crearCargo_usaMontoPorDefectoDeConfiguracionSiNoSeEspecifica() {
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(pagoRepository.existsByAlumno_IdAndPeriodo(eq(10L), any())).thenReturn(false);

        var request = new CrearPagoRequest(null, null, LocalDate.now().plusDays(10), null);
        Pago resultado = pagoService.crearCargo(10L, request);

        assertThat(resultado.getMonto()).isEqualByComparingTo("600.00");
        assertThat(resultado.getPeriodo()).isEqualTo(YearMonth.now());
    }

    @Test
    void crearCargo_fallaSiYaExisteUnCargoParaEseAlumnoYPeriodo() {
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(pagoRepository.existsByAlumno_IdAndPeriodo(eq(10L), any())).thenReturn(true);

        var request = new CrearPagoRequest(new BigDecimal("600.00"), YearMonth.now(), LocalDate.now().plusDays(10), null);

        assertThatThrownBy(() -> pagoService.crearCargo(10L, request))
                .isInstanceOf(BusinessRuleException.class);
        verify(pagoRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- transacciones admin

    @Test
    void registrarTransaccionAdmin_quedaConfirmadaYElPagoQuedaPagadoSiCubreElTotal() {
        Pago pago = pagoDe600(EstadoPago.PENDIENTE);
        when(pagoRepository.findById(50L)).thenReturn(Optional.of(pago));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(
                Usuario.builder().id(1L).email("admin@test.com").nombre("Admin").role(Role.ADMIN).build());
        when(pagoTransaccionRepository.sumConfirmadoByPagoId(50L)).thenReturn(new BigDecimal("600.00"));

        var request = new RegistrarTransaccionRequest(new BigDecimal("600.00"), null, MetodoPago.TRANSFERENCIA, "ref-1");
        PagoTransaccion resultado = pagoService.registrarTransaccionAdmin(50L, 1L, request);

        assertThat(resultado.getEstado()).isEqualTo(EstadoTransaccion.CONFIRMADA);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PAGADO);
    }

    @Test
    void registrarTransaccionAdmin_dejaElPagoEnParcialSiNoCubreElTotal() {
        Pago pago = pagoDe600(EstadoPago.PENDIENTE);
        when(pagoRepository.findById(50L)).thenReturn(Optional.of(pago));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(
                Usuario.builder().id(1L).email("admin@test.com").nombre("Admin").role(Role.ADMIN).build());
        when(pagoTransaccionRepository.sumConfirmadoByPagoId(50L)).thenReturn(new BigDecimal("300.00"));

        var request = new RegistrarTransaccionRequest(new BigDecimal("300.00"), null, MetodoPago.EFECTIVO, null);
        pagoService.registrarTransaccionAdmin(50L, 1L, request);

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PARCIAL);
    }

    // ---------------------------------------------------------------- autorreporte del alumno

    @Test
    void autorreportarPago_quedaPendienteYNoMuevoElBalanceDelPago() {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        Pago pago = pagoDe600(EstadoPago.PENDIENTE);
        when(pagoRepository.findById(50L)).thenReturn(Optional.of(pago));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(alumno.getUsuario());

        var request = new RegistrarTransaccionRequest(new BigDecimal("600.00"), null, MetodoPago.TRANSFERENCIA, "comprobante-123");
        PagoTransaccion resultado = pagoService.autorreportarPago(1L, 50L, request);

        assertThat(resultado.getEstado()).isEqualTo(EstadoTransaccion.PENDIENTE);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        verify(pagoRepository, never()).save(any());
        verify(pagoTransaccionRepository, never()).sumConfirmadoByPagoId(any());
    }

    @Test
    void autorreportarPago_fallaSiElPagoNoPerteneceAlAlumno() {
        Usuario otroUsuario = Usuario.builder().id(2L).email("otro@test.com").nombre("Otro")
                .password("hash").role(Role.ALUMNO).enabled(true).build();
        Alumno otroAlumno = Alumno.builder().id(11L).usuario(otroUsuario).fechaInscripcion(LocalDate.now()).activo(true).build();

        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        Pago pagoDeOtro = Pago.builder().id(50L).alumno(otroAlumno).monto(new BigDecimal("600.00"))
                .periodo(YearMonth.now()).fechaLimite(LocalDate.now().plusDays(5)).estado(EstadoPago.PENDIENTE).build();
        when(pagoRepository.findById(50L)).thenReturn(Optional.of(pagoDeOtro));

        var request = new RegistrarTransaccionRequest(new BigDecimal("600.00"), null, MetodoPago.TRANSFERENCIA, null);

        assertThatThrownBy(() -> pagoService.autorreportarPago(1L, 50L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- confirmar / rechazar

    @Test
    void confirmarTransaccion_actualizaElBalanceDelPago() {
        Pago pago = pagoDe600(EstadoPago.PENDIENTE);
        PagoTransaccion transaccion = PagoTransaccion.builder().id(80L).pago(pago)
                .monto(new BigDecimal("600.00")).estado(EstadoTransaccion.PENDIENTE).build();

        when(pagoTransaccionRepository.findById(80L)).thenReturn(Optional.of(transaccion));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(
                Usuario.builder().id(1L).email("admin@test.com").nombre("Admin").role(Role.ADMIN).build());
        when(pagoTransaccionRepository.sumConfirmadoByPagoId(50L)).thenReturn(new BigDecimal("600.00"));

        PagoTransaccion resultado = pagoService.confirmarTransaccion(80L, 1L);

        assertThat(resultado.getEstado()).isEqualTo(EstadoTransaccion.CONFIRMADA);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PAGADO);
    }

    @Test
    void rechazarTransaccion_noRecalculaElBalanceDelPago() {
        Pago pago = pagoDe600(EstadoPago.PENDIENTE);
        PagoTransaccion transaccion = PagoTransaccion.builder().id(80L).pago(pago)
                .monto(new BigDecimal("600.00")).estado(EstadoTransaccion.PENDIENTE).build();

        when(pagoTransaccionRepository.findById(80L)).thenReturn(Optional.of(transaccion));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(
                Usuario.builder().id(1L).email("admin@test.com").nombre("Admin").role(Role.ADMIN).build());

        PagoTransaccion resultado = pagoService.rechazarTransaccion(80L, 1L, "Comprobante no válido");

        assertThat(resultado.getEstado()).isEqualTo(EstadoTransaccion.RECHAZADA);
        assertThat(resultado.getMotivoRechazo()).isEqualTo("Comprobante no válido");
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        verify(pagoTransaccionRepository, never()).sumConfirmadoByPagoId(any());
    }

    @Test
    void confirmarTransaccion_fallaSiLaTransaccionYaFueRevisada() {
        PagoTransaccion transaccion = PagoTransaccion.builder().id(80L).pago(pagoDe600(EstadoPago.PAGADO))
                .monto(new BigDecimal("600.00")).estado(EstadoTransaccion.CONFIRMADA).build();
        when(pagoTransaccionRepository.findById(80L)).thenReturn(Optional.of(transaccion));

        assertThatThrownBy(() -> pagoService.confirmarTransaccion(80L, 1L))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---------------------------------------------------------------- eliminar transacción

    /** Caso típico: el admin confirmó por error una transacción que el alumno autorreportó. */
    @Test
    void eliminarTransaccion_recalculaElSaldoDelPagoTrasQuitarUnaConfirmada() {
        Pago pago = pagoDe600(EstadoPago.PAGADO);
        PagoTransaccion transaccion = PagoTransaccion.builder().id(80L).pago(pago)
                .monto(new BigDecimal("600.00")).estado(EstadoTransaccion.CONFIRMADA).build();
        when(pagoTransaccionRepository.findById(80L)).thenReturn(Optional.of(transaccion));
        // Tras borrarla, la suma de confirmadas para este pago ya no la incluye.
        when(pagoTransaccionRepository.sumConfirmadoByPagoId(50L)).thenReturn(BigDecimal.ZERO);

        pagoService.eliminarTransaccion(80L);

        verify(pagoTransaccionRepository).delete(transaccion);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void eliminarTransaccion_fallaSiNoExiste() {
        when(pagoTransaccionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pagoService.eliminarTransaccion(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(pagoTransaccionRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------- actualizarCargo

    @Test
    void actualizarCargo_cambiaElPeriodoSiNoChocaConOtroCargo() {
        Pago pago = pagoDe600(EstadoPago.PENDIENTE);
        YearMonth nuevoPeriodo = YearMonth.now().plusMonths(1);
        when(pagoRepository.findById(50L)).thenReturn(Optional.of(pago));
        when(pagoRepository.existsByAlumno_IdAndPeriodo(10L, nuevoPeriodo)).thenReturn(false);
        when(pagoTransaccionRepository.sumConfirmadoByPagoId(50L)).thenReturn(BigDecimal.ZERO);

        var request = new ActualizarPagoRequest(null, nuevoPeriodo, null, null);
        Pago resultado = pagoService.actualizarCargo(50L, request);

        assertThat(resultado.getPeriodo()).isEqualTo(nuevoPeriodo);
    }

    @Test
    void actualizarCargo_fallaSiElNuevoPeriodoYaTieneOtroCargoDelMismoAlumno() {
        Pago pago = pagoDe600(EstadoPago.PENDIENTE);
        YearMonth periodoOcupado = YearMonth.now().plusMonths(1);
        when(pagoRepository.findById(50L)).thenReturn(Optional.of(pago));
        when(pagoRepository.existsByAlumno_IdAndPeriodo(10L, periodoOcupado)).thenReturn(true);

        var request = new ActualizarPagoRequest(null, periodoOcupado, null, null);

        assertThatThrownBy(() -> pagoService.actualizarCargo(50L, request))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(pago.getPeriodo()).isEqualTo(YearMonth.now());
    }

    @Test
    void actualizarCargo_noValidaChoqueSiElPeriodoNoCambia() {
        Pago pago = pagoDe600(EstadoPago.PENDIENTE);
        when(pagoRepository.findById(50L)).thenReturn(Optional.of(pago));
        when(pagoTransaccionRepository.sumConfirmadoByPagoId(50L)).thenReturn(BigDecimal.ZERO);

        var request = new ActualizarPagoRequest(null, YearMonth.now(), null, null);
        pagoService.actualizarCargo(50L, request);

        verify(pagoRepository, never()).existsByAlumno_IdAndPeriodo(any(), any());
    }

    @Test
    void actualizarCargo_recalculaEstadoSiElNuevoMontoYaNoCubreLoPagado() {
        Pago pago = pagoDe600(EstadoPago.PAGADO);
        when(pagoRepository.findById(50L)).thenReturn(Optional.of(pago));
        when(pagoTransaccionRepository.sumConfirmadoByPagoId(50L)).thenReturn(new BigDecimal("600.00"));

        var request = new ActualizarPagoRequest(new BigDecimal("900.00"), null, null, null);
        Pago resultado = pagoService.actualizarCargo(50L, request);

        assertThat(resultado.getMonto()).isEqualByComparingTo("900.00");
        assertThat(resultado.getEstado()).isEqualTo(EstadoPago.PARCIAL);
    }

    // ---------------------------------------------------------------- vencido (derivado, no persistido)

    @Test
    void esVencido_esVerdaderoCuandoLaFechaLimitePasoYNoEstaPagado() {
        Pago pago = Pago.builder().id(50L).alumno(alumno).monto(new BigDecimal("600.00"))
                .periodo(YearMonth.now().minusMonths(1)).fechaLimite(LocalDate.now().minusDays(3))
                .estado(EstadoPago.PENDIENTE).build();

        assertThat(pagoService.esVencido(pago)).isTrue();
    }

    @Test
    void esVencido_esFalsoCuandoYaEstaPagadoAunqueLaFechaLimiteHayaPasado() {
        Pago pago = Pago.builder().id(50L).alumno(alumno).monto(new BigDecimal("600.00"))
                .periodo(YearMonth.now().minusMonths(1)).fechaLimite(LocalDate.now().minusDays(3))
                .estado(EstadoPago.PAGADO).build();

        assertThat(pagoService.esVencido(pago)).isFalse();
    }
}

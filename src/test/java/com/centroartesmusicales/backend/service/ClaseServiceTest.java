package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.dto.clase.AsignacionCicloItem;
import com.centroartesmusicales.backend.dto.clase.ProgramarClaseRequest;
import com.centroartesmusicales.backend.dto.clase.ReagendarRequest;
import com.centroartesmusicales.backend.dto.clase.ResumenMesResponse;
import com.centroartesmusicales.backend.dto.clase.SolicitarReagendacionRequest;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ConflictoHorarioException;
import com.centroartesmusicales.backend.exception.LimiteMensualExcedidoException;
import com.centroartesmusicales.backend.exception.PlazoReagendacionExpiradoException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.Clase;
import com.centroartesmusicales.backend.model.EstadoClase;
import com.centroartesmusicales.backend.model.EstadoSolicitud;
import com.centroartesmusicales.backend.model.Instrumento;
import com.centroartesmusicales.backend.model.Profesor;
import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.SolicitudReagendacion;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.AlumnoInstrumentoCupoRepository;
import com.centroartesmusicales.backend.repository.ClaseRepository;
import com.centroartesmusicales.backend.repository.SolicitudReagendacionRepository;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
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

/**
 * Unit tests for the monthly-cap / schedule-conflict / reschedule-notice-window rules — the
 * riskiest logic in the backend (see plan phase 3). Repositories are mocked with Mockito rather
 * than exercised against a real MySQL via Testcontainers, since Docker was not running in the
 * dev environment at implementation time; the SQL-level correctness of the exclude-by-id filter
 * itself should still be verified against a real database once available (see README).
 */
@ExtendWith(MockitoExtension.class)
class ClaseServiceTest {

    @Mock
    private ClaseRepository claseRepository;
    @Mock
    private SolicitudReagendacionRepository solicitudReagendacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AlumnoService alumnoService;
    @Mock
    private ProfesorService profesorService;
    @Mock
    private AlumnoInstrumentoCupoRepository cupoRepository;

    private ClaseService claseService;

    private Alumno alumno;
    private Profesor profesor;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties(
                new AppProperties.Jwt("unit-test-secret-unit-test-secret-32b", 3_600_000L),
                new AppProperties.Cors(List.of("http://localhost:3000")),
                new AppProperties.Admin(new AppProperties.Admin.Bootstrap("", "", "Administrador")),
                "America/Mexico_City",
                new AppProperties.Clases(4, 4, 60),
                new AppProperties.Pagos(new BigDecimal("600.00")),
                new AppProperties.Stripe("", "", ""),
                "http://localhost:5500"
        );

        claseService = new ClaseService(claseRepository, solicitudReagendacionRepository, usuarioRepository,
                alumnoService, profesorService, cupoRepository, appProperties);

        Usuario usuario = Usuario.builder().id(1L).email("valentina@test.com").nombre("Valentina")
                .password("hash").role(Role.ALUMNO).enabled(true).build();
        alumno = Alumno.builder().id(10L).usuario(usuario).fechaInscripcion(LocalDate.now()).activo(true).build();
        profesor = Profesor.builder().id(20L).nombreCompleto("Profesor Uno")
                .especialidades(java.util.Set.of(Instrumento.PIANO)).activo(true).build();

        lenient().when(claseRepository.save(any(Clase.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private LocalDateTime enTresDiasA(int hora) {
        return LocalDateTime.now().plusDays(3).withHour(hora).withMinute(0).withSecond(0).withNano(0);
    }

    // ---------------------------------------------------------------- programar

    @Test
    void programar_exitoso_cuandoNoHayConflictoNiCupoExcedido() {
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(profesorService.obtenerPorId(20L)).thenReturn(profesor);
        when(claseRepository.findCandidatosConflicto(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(0L);

        var request = new ProgramarClaseRequest(10L, 20L, Instrumento.PIANO, enTresDiasA(10), 60, null);
        Clase resultado = claseService.programar(request);

        assertThat(resultado.getEstado()).isEqualTo(EstadoClase.PROGRAMADA);
        assertThat(resultado.getClaseOriginal()).isNull();
    }

    @Test
    void programar_fallaCuandoAlumnoYaAlcanzoElLimiteMensual() {
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(profesorService.obtenerPorId(20L)).thenReturn(profesor);
        when(claseRepository.findCandidatosConflicto(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(4L);

        var request = new ProgramarClaseRequest(10L, 20L, Instrumento.PIANO, enTresDiasA(10), 60, null);

        assertThatThrownBy(() -> claseService.programar(request))
                .isInstanceOf(LimiteMensualExcedidoException.class);
        verify(claseRepository, never()).save(any());
    }

    @Test
    void programar_fallaCuandoElProfesorYaTieneClaseEnEseHorario() {
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(profesorService.obtenerPorId(20L)).thenReturn(profesor);

        LocalDateTime fechaSolicitada = enTresDiasA(10);
        Clase existente = Clase.builder().id(999L).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.GUITARRA_ACUSTICA).fechaHora(fechaSolicitada.plusMinutes(30))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();

        when(claseRepository.findCandidatosConflicto(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(existente));

        var request = new ProgramarClaseRequest(10L, 20L, Instrumento.PIANO, fechaSolicitada, 60, null);

        assertThatThrownBy(() -> claseService.programar(request))
                .isInstanceOf(ConflictoHorarioException.class);
    }

    @Test
    void programar_fallaCuandoElAlumnoYaTieneOtraClaseEnEseHorarioConOtroProfesor() {
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(profesorService.obtenerPorId(20L)).thenReturn(profesor);

        Profesor otroProfesor = Profesor.builder().id(21L).nombreCompleto("Profesor Dos").activo(true).build();
        LocalDateTime fechaSolicitada = enTresDiasA(10);
        Clase existente = Clase.builder().id(998L).alumno(alumno).profesor(otroProfesor)
                .instrumento(Instrumento.CANTO).fechaHora(fechaSolicitada)
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();

        when(claseRepository.findCandidatosConflicto(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(existente));

        var request = new ProgramarClaseRequest(10L, 20L, Instrumento.PIANO, fechaSolicitada, 60, null);

        assertThatThrownBy(() -> claseService.programar(request))
                .isInstanceOf(ConflictoHorarioException.class)
                .hasMessageContaining("alumno");
    }

    // ---------------------------------------------------------------- resumenMes

    @Test
    void resumenMes_muestraElLimiteCompletoDisponibleCuandoElCicloVigenteApenasEmpiezaYEsFuturo() {
        // fechaPrimeraClase en el pasado lejano, última clase activa a 7 días de HOY (en el
        // futuro): el ciclo vigente arranca hoy y ninguna de sus 4 clases ha pasado todavía.
        LocalDate fechaPrimeraClase = LocalDate.now().minusWeeks(12);
        LocalDate inicioCicloVigenteEsperado = LocalDate.now();
        alumno.setFechaPrimeraClase(fechaPrimeraClase);
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);

        Clase ultimaClaseDelCicloVigente = claseEn(inicioCicloVigenteEsperado.plusWeeks(3));
        when(claseRepository.findActivasDesde(eq(10L), any(), any()))
                .thenReturn(List.of(ultimaClaseDelCicloVigente));
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(0L);

        ResumenMesResponse resumen = claseService.resumenMes(10L);

        assertThat(resumen.clasesTomadas()).isEqualTo(0);
        assertThat(resumen.clasesDisponibles()).isEqualTo(4);
    }

    /** Reproduce el caso de Alexandra: ciclo 2 recién generado (12-sep a 3-oct), consultado el
     *  mismo día en que se pagó (5-sep, antes de que arranque) -> debe ser 4/0, no 3/1. Aquí:
     *  ciclo anterior CERRADO (hoy-4sem a hoy-1sem) + ciclo vigente que arranca HOY mismo
     *  (hoy, hoy+1sem, hoy+2sem, hoy+3sem) y todavía no ha pasado ninguna de sus clases. */
    @Test
    void resumenMes_noCuentaClasesDeUnCicloQueTodaviaNoArrancaAunqueElAlumnoTengaCiclosAnterioresCerrados() {
        LocalDate fechaPrimeraClase = LocalDate.now().minusWeeks(8);
        LocalDate inicioCicloAnterior = LocalDate.now().minusWeeks(4);
        LocalDate inicioCicloVigente = LocalDate.now();
        alumno.setFechaPrimeraClase(fechaPrimeraClase);
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);

        List<Clase> todasActivas = List.of(
                claseEn(inicioCicloAnterior), claseEn(inicioCicloAnterior.plusWeeks(1)),
                claseEn(inicioCicloAnterior.plusWeeks(2)), claseEn(inicioCicloAnterior.plusWeeks(3)),
                claseEn(inicioCicloVigente), claseEn(inicioCicloVigente.plusWeeks(1)),
                claseEn(inicioCicloVigente.plusWeeks(2)), claseEn(inicioCicloVigente.plusWeeks(3)));
        when(claseRepository.findActivasDesde(eq(10L), any(), any())).thenReturn(todasActivas);
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(0L);

        ResumenMesResponse resumen = claseService.resumenMes(10L);

        assertThat(resumen.clasesTomadas()).isEqualTo(0);
        assertThat(resumen.clasesDisponibles()).isEqualTo(4);
    }

    @Test
    void resumenMes_caeAlMesCalendarioActualCuandoElAlumnoNoTieneFechaPrimeraClaseTodavia() {
        alumno.setFechaPrimeraClase(null);
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(0L);

        ResumenMesResponse resumen = claseService.resumenMes(10L);

        assertThat(resumen.periodo()).isEqualTo(YearMonth.now().toString());
        assertThat(resumen.clasesDisponibles()).isEqualTo(4);
        verify(claseRepository, never()).findActivasDesde(any(), any(), any());
    }

    @Test
    void resumenMesPropio_resuelveElAlumnoPorUsuarioIdAntesDeConsultarElResumen() {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        alumno.setFechaPrimeraClase(null);
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(0L);

        ResumenMesResponse resumen = claseService.resumenMesPropio(1L);

        assertThat(resumen).isNotNull();
        verify(alumnoService).obtenerPorUsuarioId(1L);
    }

    // ---------------------------------------------------------------- resolverFechasCiclo

    @Test
    void resolverFechasCiclo_proyectaDesdeFechaPrimeraClaseCuandoNoHayClasesActivas() {
        when(claseRepository.findActivasDesde(eq(10L), any(), any())).thenReturn(List.of());

        List<LocalDate> fechas = claseService.resolverFechasCiclo(10L, LocalDate.of(2026, 8, 15));

        assertThat(fechas).containsExactly(
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 29), LocalDate.of(2026, 9, 5));
    }

    /** Con ambos ciclos ya agendados, debe mostrar el SEGUNDO (vigente), no quedarse en el primero. */
    @Test
    void resolverFechasCiclo_muestraElCicloVigenteNoElPrimeroCuandoYaHayVariosCiclos() {
        List<Clase> cicloUno = List.of(
                claseEn(LocalDate.of(2026, 8, 15)), claseEn(LocalDate.of(2026, 8, 22)),
                claseEn(LocalDate.of(2026, 8, 30)), claseEn(LocalDate.of(2026, 9, 5)));
        List<Clase> cicloDos = List.of(
                claseEn(LocalDate.of(2026, 9, 12)), claseEn(LocalDate.of(2026, 9, 19)),
                claseEn(LocalDate.of(2026, 9, 26)), claseEn(LocalDate.of(2026, 10, 3)));
        List<Clase> todas = new ArrayList<>(cicloUno);
        todas.addAll(cicloDos);
        when(claseRepository.findActivasDesde(eq(10L), any(), any())).thenReturn(todas);

        List<LocalDate> fechas = claseService.resolverFechasCiclo(10L, LocalDate.of(2026, 8, 15));

        assertThat(fechas).containsExactly(
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19),
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 3));
    }

    /** Ciclo vigente generado solo a medias: completa con proyección sin colar fechas del ciclo anterior. */
    @Test
    void resolverFechasCiclo_completaConProyeccionSinMezclarConElCicloAnteriorCuandoElVigenteEstaAMedias() {
        List<Clase> cicloUno = List.of(
                claseEn(LocalDate.of(2026, 8, 15)), claseEn(LocalDate.of(2026, 8, 22)),
                claseEn(LocalDate.of(2026, 8, 30)), claseEn(LocalDate.of(2026, 9, 5)));
        List<Clase> cicloDosParcial = List.of(
                claseEn(LocalDate.of(2026, 9, 12)), claseEn(LocalDate.of(2026, 9, 19)));
        List<Clase> todas = new ArrayList<>(cicloUno);
        todas.addAll(cicloDosParcial);
        when(claseRepository.findActivasDesde(eq(10L), any(), any())).thenReturn(todas);

        List<LocalDate> fechas = claseService.resolverFechasCiclo(10L, LocalDate.of(2026, 8, 15));

        assertThat(fechas).containsExactly(
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19),
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 3));
    }

    private Clase claseEn(LocalDate fecha) {
        return Clase.builder().id((long) fecha.hashCode()).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(fecha.atTime(16, 0))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
    }

    // ---------------------------------------------------------------- programarCiclo

    private AsignacionCicloItem asignacionUnica(int cantidad) {
        return new AsignacionCicloItem(Instrumento.PIANO, 20L, java.time.LocalTime.of(16, 0), cantidad);
    }

    @Test
    void programarCiclo_primerCicloArrancaEnFechaPrimeraClaseCuandoNoHayClasesPrevias() {
        alumno.setFechaPrimeraClase(LocalDate.of(2026, 8, 15));
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(profesorService.obtenerPorId(20L)).thenReturn(profesor);
        when(claseRepository.findActivasDesde(eq(10L), any(), any())).thenReturn(List.of());
        when(claseRepository.findCandidatosConflicto(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(0L);

        List<Clase> creadas = claseService.programarCiclo(10L, List.of(asignacionUnica(4)), null, null);

        List<LocalDate> fechas = creadas.stream().map(c -> c.getFechaHora().toLocalDate()).toList();
        assertThat(fechas).containsExactly(
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 29),
                LocalDate.of(2026, 9, 5));
    }

    /**
     * Reproduce el caso de Alexandra: primer ciclo 15/22/29-ago (la del 29 fue reagendada al
     * 30-ago) + 5-sep. Al generar el SEGUNDO ciclo, antes se recalculaba desde fechaPrimeraClase
     * (15-ago) otra vez y chocaba contra esas mismas clases ya existentes (ConflictoHorarioException
     * falso). Debe arrancar una semana después de la última clase activa (5-sep) -> 12/19/26-sep + 3-oct.
     */
    @Test
    void programarCiclo_segundoCicloArrancaUnaSemanaDespuesDeLaUltimaClaseActivaDelAlumno() {
        alumno.setFechaPrimeraClase(LocalDate.of(2026, 8, 15));
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);
        when(profesorService.obtenerPorId(20L)).thenReturn(profesor);

        Clase clase1 = Clase.builder().id(1L).alumno(alumno).profesor(profesor).instrumento(Instrumento.PIANO)
                .fechaHora(LocalDate.of(2026, 8, 15).atTime(16, 0)).duracionMinutos(60)
                .estado(EstadoClase.REALIZADA).build();
        Clase clase2 = Clase.builder().id(2L).alumno(alumno).profesor(profesor).instrumento(Instrumento.PIANO)
                .fechaHora(LocalDate.of(2026, 8, 22).atTime(16, 0)).duracionMinutos(60)
                .estado(EstadoClase.REALIZADA).build();
        // La del 29-ago se reagendó al 30-ago: la original queda REAGENDADA (no debe contar) y
        // la nueva fila PROGRAMADA es la que de verdad representa esa semana del ciclo.
        Clase clase3Reagendada = Clase.builder().id(3L).alumno(alumno).profesor(profesor).instrumento(Instrumento.PIANO)
                .fechaHora(LocalDate.of(2026, 8, 30).atTime(16, 0)).duracionMinutos(60)
                .estado(EstadoClase.PROGRAMADA).build();
        Clase clase4 = Clase.builder().id(4L).alumno(alumno).profesor(profesor).instrumento(Instrumento.PIANO)
                .fechaHora(LocalDate.of(2026, 9, 5).atTime(16, 0)).duracionMinutos(60)
                .estado(EstadoClase.PROGRAMADA).build();

        when(claseRepository.findActivasDesde(eq(10L), any(), any()))
                .thenReturn(List.of(clase1, clase2, clase3Reagendada, clase4));
        when(claseRepository.findCandidatosConflicto(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(0L);

        List<Clase> creadas = claseService.programarCiclo(10L, List.of(asignacionUnica(4)), null, null);

        List<LocalDate> fechas = creadas.stream().map(c -> c.getFechaHora().toLocalDate()).toList();
        assertThat(fechas).containsExactly(
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 19),
                LocalDate.of(2026, 9, 26),
                LocalDate.of(2026, 10, 3));
    }

    @Test
    void programarCiclo_fallaCuandoLasCantidadesNoSumanCuatro() {
        alumno.setFechaPrimeraClase(LocalDate.of(2026, 8, 15));
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);

        assertThatThrownBy(() -> claseService.programarCiclo(10L, List.of(asignacionUnica(3)), null, null))
                .isInstanceOf(BusinessRuleException.class);
        verify(claseRepository, never()).save(any());
    }

    @Test
    void programarCiclo_fallaCuandoElAlumnoNoTieneFechaPrimeraClase() {
        alumno.setFechaPrimeraClase(null);
        when(alumnoService.obtenerPorId(10L)).thenReturn(alumno);

        assertThatThrownBy(() -> claseService.programarCiclo(10L, List.of(asignacionUnica(4)), null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---------------------------------------------------------------- solicitudes de reagendo

    @Test
    void crearSolicitud_fallaSiFaltanMenosDeLasHorasMinimasParaLaClase() {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        Clase clase = Clase.builder().id(100L).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(LocalDateTime.now().plusHours(1))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
        when(claseRepository.findById(100L)).thenReturn(Optional.of(clase));
        when(solicitudReagendacionRepository.existsByClase_IdAndEstado(100L, EstadoSolicitud.PENDIENTE))
                .thenReturn(false);

        var request = new SolicitarReagendacionRequest(enTresDiasA(10), null, "no puedo asistir");

        assertThatThrownBy(() -> claseService.crearSolicitud(1L, 100L, request))
                .isInstanceOf(PlazoReagendacionExpiradoException.class);
        verify(solicitudReagendacionRepository, never()).save(any());
    }

    @Test
    void crearSolicitud_exitosaCuandoHaySuficienteAnticipacion() {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        Clase clase = Clase.builder().id(100L).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(LocalDateTime.now().plusHours(10))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
        when(claseRepository.findById(100L)).thenReturn(Optional.of(clase));
        when(solicitudReagendacionRepository.existsByClase_IdAndEstado(100L, EstadoSolicitud.PENDIENTE))
                .thenReturn(false);
        when(solicitudReagendacionRepository.save(any(SolicitudReagendacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var request = new SolicitarReagendacionRequest(enTresDiasA(10), null, "no puedo asistir");
        SolicitudReagendacion resultado = claseService.crearSolicitud(1L, 100L, request);

        assertThat(resultado.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(resultado.getClase()).isEqualTo(clase);
    }

    @Test
    void crearSolicitud_fallaSiLaClaseNoPerteneceAlAlumno() {
        Usuario otroUsuario = Usuario.builder().id(2L).email("otro@test.com").nombre("Otro")
                .password("hash").role(Role.ALUMNO).enabled(true).build();
        Alumno otroAlumno = Alumno.builder().id(11L).usuario(otroUsuario).fechaInscripcion(LocalDate.now())
                .activo(true).build();

        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        Clase claseDeOtroAlumno = Clase.builder().id(100L).alumno(otroAlumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(enTresDiasA(10))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
        when(claseRepository.findById(100L)).thenReturn(Optional.of(claseDeOtroAlumno));

        var request = new SolicitarReagendacionRequest(enTresDiasA(11), null, null);

        assertThatThrownBy(() -> claseService.crearSolicitud(1L, 100L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void crearSolicitud_fallaSiYaHayUnaSolicitudPendienteParaLaMismaClase() {
        when(alumnoService.obtenerPorUsuarioId(1L)).thenReturn(alumno);
        Clase clase = Clase.builder().id(100L).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(enTresDiasA(10))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
        when(claseRepository.findById(100L)).thenReturn(Optional.of(clase));
        when(solicitudReagendacionRepository.existsByClase_IdAndEstado(100L, EstadoSolicitud.PENDIENTE))
                .thenReturn(true);

        var request = new SolicitarReagendacionRequest(enTresDiasA(11), null, null);

        assertThatThrownBy(() -> claseService.crearSolicitud(1L, 100L, request))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---------------------------------------------------------------- reagendo: exclusión de la propia clase

    @Test
    void reagendarDirecto_excluyeLaClaseOriginalDelConteoDeConflictoYCupo() {
        Clase original = Clase.builder().id(500L).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(enTresDiasA(10))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
        when(claseRepository.findById(500L)).thenReturn(Optional.of(original));
        when(claseRepository.findCandidatosConflicto(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(0L);

        LocalDateTime nuevaFecha = enTresDiasA(14);
        var request = new ReagendarRequest(nuevaFecha, null);
        Clase nueva = claseService.reagendarDirecto(500L, request);

        assertThat(original.getEstado()).isEqualTo(EstadoClase.REAGENDADA);
        assertThat(nueva.getEstado()).isEqualTo(EstadoClase.PROGRAMADA);
        assertThat(nueva.getClaseOriginal()).isEqualTo(original);

        verify(claseRepository).countOcupadasEnRango(eq(alumno.getId()), any(), any(), any(), eq(500L));
        verify(claseRepository).findCandidatosConflicto(eq(profesor.getId()), eq(alumno.getId()), any(), any(), any(), eq(500L));
    }

    // ---------------------------------------------------------------- asistencia / cancelación

    @Test
    void marcarAsistencia_aceptaAusenteComoEstadoValido() {
        Clase clase = Clase.builder().id(100L).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(enTresDiasA(10))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
        when(claseRepository.findById(100L)).thenReturn(Optional.of(clase));

        Clase resultado = claseService.marcarAsistencia(100L, EstadoClase.AUSENTE);

        assertThat(resultado.getEstado()).isEqualTo(EstadoClase.AUSENTE);
    }

    @Test
    void marcarAsistencia_rechazaEstadosQueNoSonDeAsistencia() {
        assertThatThrownBy(() -> claseService.marcarAsistencia(100L, EstadoClase.CANCELADA))
                .isInstanceOf(IllegalArgumentException.class);
        verify(claseRepository, never()).findById(any());
    }

    @Test
    void cancelar_marcaLaClaseComoCanceladaYConservaElMotivoEnNotas() {
        Clase clase = Clase.builder().id(100L).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(enTresDiasA(10))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
        when(claseRepository.findById(100L)).thenReturn(Optional.of(clase));

        claseService.cancelar(100L, "El profesor tuvo una emergencia");

        ArgumentCaptor<Clase> captor = ArgumentCaptor.forClass(Clase.class);
        verify(claseRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoClase.CANCELADA);
        assertThat(captor.getValue().getNotas()).contains("emergencia");
    }

    // ---------------------------------------------------------------- aprobar / rechazar solicitud

    @Test
    void aprobarSolicitud_ejecutaElReagendoYQuedaAprobadaConLaClaseNuevaEnlazada() {
        Clase original = Clase.builder().id(500L).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(enTresDiasA(10))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
        SolicitudReagendacion solicitud = SolicitudReagendacion.builder().id(700L).clase(original)
                .fechaHoraPropuesta(enTresDiasA(16)).estado(EstadoSolicitud.PENDIENTE).build();

        when(solicitudReagendacionRepository.findById(700L)).thenReturn(Optional.of(solicitud));
        when(claseRepository.findCandidatosConflicto(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(claseRepository.countOcupadasEnRango(any(), any(), any(), any(), any())).thenReturn(0L);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(
                Usuario.builder().id(1L).email("admin@test.com").nombre("Admin").role(Role.ADMIN).build());
        when(solicitudReagendacionRepository.save(any(SolicitudReagendacion.class))).thenAnswer(inv -> inv.getArgument(0));

        SolicitudReagendacion resultado = claseService.aprobarSolicitud(700L, 1L);

        assertThat(resultado.getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(resultado.getClaseNueva()).isNotNull();
        assertThat(resultado.getClaseNueva().getEstado()).isEqualTo(EstadoClase.PROGRAMADA);
        assertThat(original.getEstado()).isEqualTo(EstadoClase.REAGENDADA);
    }

    @Test
    void rechazarSolicitud_marcaRechazadaSinTocarLaClaseOriginal() {
        Clase original = Clase.builder().id(500L).alumno(alumno).profesor(profesor)
                .instrumento(Instrumento.PIANO).fechaHora(enTresDiasA(10))
                .duracionMinutos(60).estado(EstadoClase.PROGRAMADA).build();
        SolicitudReagendacion solicitud = SolicitudReagendacion.builder().id(700L).clase(original)
                .fechaHoraPropuesta(enTresDiasA(16)).estado(EstadoSolicitud.PENDIENTE).build();

        when(solicitudReagendacionRepository.findById(700L)).thenReturn(Optional.of(solicitud));
        when(usuarioRepository.getReferenceById(1L)).thenReturn(
                Usuario.builder().id(1L).email("admin@test.com").nombre("Admin").role(Role.ADMIN).build());
        when(solicitudReagendacionRepository.save(any(SolicitudReagendacion.class))).thenAnswer(inv -> inv.getArgument(0));

        SolicitudReagendacion resultado = claseService.rechazarSolicitud(700L, 1L, "El profesor no está disponible");

        assertThat(resultado.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(resultado.getMotivoRechazo()).isEqualTo("El profesor no está disponible");
        assertThat(original.getEstado()).isEqualTo(EstadoClase.PROGRAMADA);
        verify(claseRepository, never()).save(any());
    }
}

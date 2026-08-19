package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.dto.clase.ProgramarClaseRequest;
import com.centroartesmusicales.backend.dto.clase.ReagendarRequest;
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

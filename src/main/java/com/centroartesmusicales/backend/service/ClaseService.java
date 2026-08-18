package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.dto.clase.ProgramarClaseRequest;
import com.centroartesmusicales.backend.dto.clase.ReagendarRequest;
import com.centroartesmusicales.backend.dto.clase.ResumenMesResponse;
import com.centroartesmusicales.backend.dto.clase.SolicitarReagendacionRequest;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ConflictoHorarioException;
import com.centroartesmusicales.backend.exception.EstadoClaseInvalidoException;
import com.centroartesmusicales.backend.exception.LimiteMensualExcedidoException;
import com.centroartesmusicales.backend.exception.PlazoReagendacionExpiradoException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.Clase;
import com.centroartesmusicales.backend.model.EstadoClase;
import com.centroartesmusicales.backend.model.EstadoSolicitud;
import com.centroartesmusicales.backend.model.Instrumento;
import com.centroartesmusicales.backend.model.Profesor;
import com.centroartesmusicales.backend.model.SolicitudReagendacion;
import com.centroartesmusicales.backend.repository.ClaseRepository;
import com.centroartesmusicales.backend.repository.SolicitudReagendacionRepository;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
import com.centroartesmusicales.backend.util.CicloClases;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClaseService {

    private static final List<EstadoClase> ESTADOS_OCUPAN_CUPO =
            List.of(EstadoClase.PROGRAMADA, EstadoClase.REALIZADA, EstadoClase.AUSENTE);

    private final ClaseRepository claseRepository;
    private final SolicitudReagendacionRepository solicitudReagendacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlumnoService alumnoService;
    private final ProfesorService profesorService;
    private final AppProperties appProperties;

    private ZoneId zoneId() {
        return ZoneId.of(appProperties.timezone());
    }

    // ---------------------------------------------------------------- lectura

    public Clase obtenerPorId(Long id) {
        return claseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clase no encontrada: " + id));
    }

    /** Ownership violations surface as 404, not 403 — avoids confirming another student's data exists. */
    public Clase obtenerPropia(Long usuarioId, Long claseId) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        Clase clase = obtenerPorId(claseId);
        if (!clase.getAlumno().getId().equals(alumno.getId())) {
            throw new ResourceNotFoundException("Clase no encontrada: " + claseId);
        }
        return clase;
    }

    public Page<Clase> listar(Long alumnoId, Long profesorId, EstadoClase estado,
                               LocalDateTime desde, LocalDateTime hasta, Pageable pageable) {
        return claseRepository.buscar(alumnoId, profesorId, estado, desde, hasta, pageable);
    }

    public Page<Clase> listarPropias(Long usuarioId, EstadoClase estado, Pageable pageable) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        return claseRepository.buscar(alumno.getId(), null, estado, null, null, pageable);
    }

    public ResumenMesResponse resumenMes(Long alumnoId, YearMonth periodo) {
        YearMonth mes = periodo != null ? periodo : YearMonth.now(zoneId());
        LocalDateTime inicio = mes.atDay(1).atStartOfDay();
        LocalDateTime fin = mes.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime ahora = LocalDateTime.now(zoneId());

        // "Ocupadas" (para el cupo disponible) cuenta también las clases futuras ya agendadas —
        // esas sí deben bloquear el cupo. "Tomadas" es solo lo que ya pasó: una PROGRAMADA en el
        // futuro (p.ej. del ciclo generado de una vez) todavía no fue tomada.
        long ocupadas = claseRepository.countOcupadasEnRango(alumnoId, ESTADOS_OCUPAN_CUPO, inicio, fin, null);
        LocalDateTime finTomadas = ahora.isBefore(fin) ? ahora : fin;
        long tomadas = claseRepository.countOcupadasEnRango(alumnoId, ESTADOS_OCUPAN_CUPO, inicio, finTomadas, null);

        int limite = appProperties.clases().limiteMensual();
        int disponibles = (int) Math.max(0, limite - ocupadas);
        return new ResumenMesResponse(mes.toString(), (int) tomadas, limite, disponibles);
    }

    public ResumenMesResponse resumenMesPropio(Long usuarioId, YearMonth periodo) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        return resumenMes(alumno.getId(), periodo);
    }

    // ---------------------------------------------------------------- escritura (admin)

    @Transactional
    public Clase programar(ProgramarClaseRequest request) {
        Alumno alumno = alumnoService.obtenerPorId(request.alumnoId());
        Profesor profesor = profesorService.obtenerPorId(request.profesorId());
        int duracion = request.duracionMinutos() != null
                ? request.duracionMinutos()
                : appProperties.clases().duracionDefaultMinutos();

        Clase clase = crearClaseInterna(alumno, profesor, request.instrumento(), request.fechaHora(), duracion, null);
        if (request.notas() != null && !request.notas().isBlank()) {
            clase.setNotas(request.notas());
            clase = claseRepository.save(clase);
        }
        return clase;
    }

    /**
     * Agenda de un solo golpe las 4 clases semanales del ciclo vigente (ver CicloClases), a partir
     * de alumno.fechaPrimeraClase. Todo o nada: si cualquiera de las 4 choca con un horario
     * ocupado o excede el cupo mensual, no se crea ninguna.
     */
    @Transactional
    public List<Clase> programarCiclo(Long alumnoId, Long profesorId, Instrumento instrumento,
                                       LocalTime horaClase, Integer duracionMinutos, String notas) {
        Alumno alumno = alumnoService.obtenerPorId(alumnoId);
        if (alumno.getFechaPrimeraClase() == null) {
            throw new BusinessRuleException("El alumno no tiene registrada su fecha de primera clase");
        }
        Profesor profesor = profesorService.obtenerPorId(profesorId);
        int duracion = duracionMinutos != null ? duracionMinutos : appProperties.clases().duracionDefaultMinutos();

        List<Clase> creadas = new ArrayList<>();
        for (LocalDate fecha : CicloClases.fechasClases(alumno.getFechaPrimeraClase())) {
            Clase clase = crearClaseInterna(alumno, profesor, instrumento, fecha.atTime(horaClase), duracion, null);
            if (notas != null && !notas.isBlank()) {
                clase.setNotas(notas);
                clase = claseRepository.save(clase);
            }
            creadas.add(clase);
        }
        return creadas;
    }

    @Transactional
    public Clase marcarAsistencia(Long claseId, EstadoClase nuevoEstado) {
        if (nuevoEstado != EstadoClase.REALIZADA && nuevoEstado != EstadoClase.AUSENTE) {
            throw new IllegalArgumentException("El estado de asistencia debe ser REALIZADA o AUSENTE");
        }
        Clase clase = obtenerPorId(claseId);
        if (clase.getEstado() != EstadoClase.PROGRAMADA) {
            throw new EstadoClaseInvalidoException("Solo se puede tomar asistencia de una clase en estado PROGRAMADA");
        }
        clase.setEstado(nuevoEstado);
        return claseRepository.save(clase);
    }

    /** Admin reschedules directly — no approval gate (the admin IS the approver) and no notice-window check. */
    @Transactional
    public Clase reagendarDirecto(Long claseId, ReagendarRequest request) {
        Clase original = obtenerPorId(claseId);
        Profesor destino = request.profesorId() != null
                ? profesorService.obtenerPorId(request.profesorId())
                : original.getProfesor();
        return ejecutarReagendo(original, destino, request.fechaHoraPropuesta());
    }

    @Transactional
    public void cancelar(Long claseId, String motivo) {
        Clase clase = obtenerPorId(claseId);
        if (clase.getEstado() != EstadoClase.PROGRAMADA) {
            throw new EstadoClaseInvalidoException("Solo se puede cancelar una clase en estado PROGRAMADA");
        }
        clase.setEstado(EstadoClase.CANCELADA);
        if (motivo != null && !motivo.isBlank()) {
            clase.setNotas(clase.getNotas() == null ? motivo : clase.getNotas() + " | " + motivo);
        }
        claseRepository.save(clase);
    }

    // ---------------------------------------------------------------- solicitudes de reagendo (alumno + admin)

    @Transactional
    public SolicitudReagendacion crearSolicitud(Long usuarioId, Long claseId, SolicitarReagendacionRequest request) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        Clase clase = obtenerPorId(claseId);
        if (!clase.getAlumno().getId().equals(alumno.getId())) {
            throw new ResourceNotFoundException("Clase no encontrada: " + claseId);
        }
        if (clase.getEstado() != EstadoClase.PROGRAMADA) {
            throw new EstadoClaseInvalidoException("Solo se puede solicitar reagendo de una clase programada");
        }
        if (solicitudReagendacionRepository.existsByClase_IdAndEstado(claseId, EstadoSolicitud.PENDIENTE)) {
            throw new BusinessRuleException("Ya existe una solicitud de reagendo pendiente para esta clase");
        }

        int horasMinimas = appProperties.clases().horasMinimasReagendar();
        LocalDateTime limitePlazo = clase.getFechaHora().minusHours(horasMinimas);
        if (LocalDateTime.now(zoneId()).isAfter(limitePlazo)) {
            throw new PlazoReagendacionExpiradoException(
                    "Ya no se puede solicitar reagendo: se requieren al menos " + horasMinimas
                            + " horas de anticipación a la clase original");
        }

        Profesor profesorPropuesto = request.profesorId() != null
                ? profesorService.obtenerPorId(request.profesorId())
                : null;

        SolicitudReagendacion solicitud = SolicitudReagendacion.builder()
                .clase(clase)
                .fechaHoraPropuesta(request.fechaHoraPropuesta())
                .profesorPropuesto(profesorPropuesto)
                .motivo(request.motivo())
                .estado(EstadoSolicitud.PENDIENTE)
                .build();

        return solicitudReagendacionRepository.save(solicitud);
    }

    public Page<SolicitudReagendacion> listarSolicitudesPropias(Long usuarioId, Pageable pageable) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        return solicitudReagendacionRepository.findByClase_Alumno_Id(alumno.getId(), pageable);
    }

    public Page<SolicitudReagendacion> listarSolicitudes(EstadoSolicitud estado, Pageable pageable) {
        if (estado != null) {
            return solicitudReagendacionRepository.findByEstado(estado, pageable);
        }
        return solicitudReagendacionRepository.findAll(pageable);
    }

    @Transactional
    public SolicitudReagendacion aprobarSolicitud(Long solicitudId, Long adminUsuarioId) {
        SolicitudReagendacion solicitud = obtenerSolicitud(solicitudId);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new EstadoClaseInvalidoException("La solicitud ya fue revisada");
        }

        Clase original = solicitud.getClase();
        Profesor destino = solicitud.getProfesorPropuesto() != null
                ? solicitud.getProfesorPropuesto()
                : original.getProfesor();
        Clase nueva = ejecutarReagendo(original, destino, solicitud.getFechaHoraPropuesta());

        solicitud.setEstado(EstadoSolicitud.APROBADA);
        solicitud.setClaseNueva(nueva);
        solicitud.setRevisadoPor(usuarioRepository.getReferenceById(adminUsuarioId));
        solicitud.setRevisadoAt(LocalDateTime.now(zoneId()));
        return solicitudReagendacionRepository.save(solicitud);
    }

    @Transactional
    public SolicitudReagendacion rechazarSolicitud(Long solicitudId, Long adminUsuarioId, String motivoRechazo) {
        SolicitudReagendacion solicitud = obtenerSolicitud(solicitudId);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new EstadoClaseInvalidoException("La solicitud ya fue revisada");
        }
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);
        solicitud.setMotivoRechazo(motivoRechazo);
        solicitud.setRevisadoPor(usuarioRepository.getReferenceById(adminUsuarioId));
        solicitud.setRevisadoAt(LocalDateTime.now(zoneId()));
        return solicitudReagendacionRepository.save(solicitud);
    }

    private SolicitudReagendacion obtenerSolicitud(Long id) {
        return solicitudReagendacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de reagendo no encontrada: " + id));
    }

    // ---------------------------------------------------------------- núcleo compartido

    /**
     * Shared by reagendarDirecto (admin, immediate) and aprobarSolicitud (alumno-requested,
     * admin-approved): marks the original class REAGENDADA and creates a new PROGRAMADA class
     * pointing back to it, after re-validating conflict + monthly cap against the target slot.
     */
    private Clase ejecutarReagendo(Clase original, Profesor profesorDestino, LocalDateTime fechaHoraDestino) {
        if (original.getEstado() != EstadoClase.PROGRAMADA) {
            throw new EstadoClaseInvalidoException("Solo se puede reagendar una clase en estado PROGRAMADA");
        }

        Clase nueva = crearClaseInterna(original.getAlumno(), profesorDestino, original.getInstrumento(),
                fechaHoraDestino, original.getDuracionMinutos(), original);

        original.setEstado(EstadoClase.REAGENDADA);
        claseRepository.save(original);

        return nueva;
    }

    private Clase crearClaseInterna(Alumno alumno, Profesor profesor, Instrumento instrumento,
                                     LocalDateTime fechaHora, int duracionMinutos, Clase claseOriginal) {
        Long excludeId = claseOriginal != null ? claseOriginal.getId() : null;
        verificarDisponibilidad(profesor.getId(), alumno.getId(), fechaHora, duracionMinutos, excludeId);
        verificarCupoMensual(alumno.getId(), fechaHora, excludeId);

        Clase clase = Clase.builder()
                .alumno(alumno)
                .profesor(profesor)
                .instrumento(instrumento)
                .fechaHora(fechaHora)
                .duracionMinutos(duracionMinutos)
                .estado(EstadoClase.PROGRAMADA)
                .claseOriginal(claseOriginal)
                .build();
        return claseRepository.save(clase);
    }

    private void verificarDisponibilidad(Long profesorId, Long alumnoId, LocalDateTime fechaHora,
                                          int duracionMinutos, Long excludeClaseId) {
        LocalDateTime finNuevo = fechaHora.plusMinutes(duracionMinutos);
        LocalDateTime desdeVentana = fechaHora.toLocalDate().atStartOfDay();
        LocalDateTime hastaVentana = desdeVentana.plusDays(1);

        List<Clase> candidatos = claseRepository.findCandidatosConflicto(
                profesorId, alumnoId, ESTADOS_OCUPAN_CUPO, desdeVentana, hastaVentana, excludeClaseId);

        for (Clase candidata : candidatos) {
            LocalDateTime inicioExistente = candidata.getFechaHora();
            LocalDateTime finExistente = inicioExistente.plusMinutes(candidata.getDuracionMinutos());
            boolean solapa = fechaHora.isBefore(finExistente) && inicioExistente.isBefore(finNuevo);
            if (solapa) {
                boolean esProfesor = candidata.getProfesor().getId().equals(profesorId);
                throw new ConflictoHorarioException(
                        "Ya existe una clase que se cruza en el horario solicitado para "
                                + (esProfesor ? "el profesor" : "el alumno"));
            }
        }
    }

    private void verificarCupoMensual(Long alumnoId, LocalDateTime fechaHora, Long excludeClaseId) {
        LocalDateTime inicioMes = fechaHora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = inicioMes.plusMonths(1);

        long ocupadas = claseRepository.countOcupadasEnRango(alumnoId, ESTADOS_OCUPAN_CUPO, inicioMes, finMes, excludeClaseId);
        int limite = appProperties.clases().limiteMensual();
        if (ocupadas >= limite) {
            throw new LimiteMensualExcedidoException(
                    "El alumno ya alcanzó el límite de " + limite + " clases para este mes");
        }
    }
}

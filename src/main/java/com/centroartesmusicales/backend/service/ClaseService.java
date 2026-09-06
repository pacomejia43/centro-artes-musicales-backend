package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.dto.clase.AsignacionCicloItem;
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
import com.centroartesmusicales.backend.model.AlumnoInstrumentoCupo;
import com.centroartesmusicales.backend.model.Clase;
import com.centroartesmusicales.backend.model.EstadoClase;
import com.centroartesmusicales.backend.model.EstadoSolicitud;
import com.centroartesmusicales.backend.model.Instrumento;
import com.centroartesmusicales.backend.model.Profesor;
import com.centroartesmusicales.backend.model.SolicitudReagendacion;
import com.centroartesmusicales.backend.repository.AlumnoInstrumentoCupoRepository;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
    private final AlumnoInstrumentoCupoRepository cupoRepository;
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

        // "Tomadas" es solo lo que ya pasó: una PROGRAMADA en el futuro (p.ej. del ciclo generado
        // de una vez) todavía no fue tomada. "Disponibles" es el resto del límite mensual — las
        // dos cifras siempre deben sumar el límite (el cupo real ya lo bloquea verificarCupoMensual,
        // que sí cuenta también las futuras; esto es solo para mostrarle el avance al alumno/admin).
        LocalDateTime finTomadas = ahora.isBefore(fin) ? ahora : fin;

        List<AlumnoInstrumentoCupo> cupos = cupoRepository.findByAlumno_IdOrderByInstrumento(alumnoId);
        if (cupos.isEmpty()) {
            long tomadas = claseRepository.countOcupadasEnRango(alumnoId, ESTADOS_OCUPAN_CUPO, inicio, finTomadas, null);
            int limite = appProperties.clases().limiteMensual();
            int disponibles = (int) Math.max(0, limite - tomadas);
            return new ResumenMesResponse(mes.toString(), (int) tomadas, limite, disponibles, List.of());
        }

        List<ResumenMesResponse.ResumenInstrumentoItem> porInstrumento = new ArrayList<>();
        int totalTomadas = 0;
        int totalLimite = 0;
        for (AlumnoInstrumentoCupo cupo : cupos) {
            long tomadasInstrumento = claseRepository.countOcupadasEnRangoPorInstrumento(
                    alumnoId, cupo.getInstrumento(), ESTADOS_OCUPAN_CUPO, inicio, finTomadas, null);
            int disponiblesInstrumento = (int) Math.max(0, cupo.getCupoMensual() - tomadasInstrumento);
            porInstrumento.add(new ResumenMesResponse.ResumenInstrumentoItem(
                    cupo.getInstrumento(), (int) tomadasInstrumento, cupo.getCupoMensual(), disponiblesInstrumento));
            totalTomadas += tomadasInstrumento;
            totalLimite += cupo.getCupoMensual();
        }
        int totalDisponibles = Math.max(0, totalLimite - totalTomadas);
        return new ResumenMesResponse(mes.toString(), totalTomadas, totalLimite, totalDisponibles, porInstrumento);
    }

    public ResumenMesResponse resumenMesPropio(Long usuarioId, YearMonth periodo) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        return resumenMes(alumno.getId(), periodo);
    }

    /**
     * Fechas del ciclo de 4 clases VIGENTE de un alumno (el más reciente, no siempre el primero).
     * Primero ubica cuándo arranca ese ciclo (mismo criterio que fechaInicioProximoCiclo, pero
     * sin el +7 días: aquí queremos el ciclo que YA está en curso, no el siguiente) y luego usa
     * las fechas reales de las clases activas que caen dentro de esa ventana de 28 días — así, si
     * el alumno reagenda una y el admin la aprueba, la clase reagendada (nueva fila PROGRAMADA)
     * reemplaza aquí a la original (que queda REAGENDADA y ya no cuenta). Si el ciclo vigente se
     * generó solo a medias (ej. un ciclo mixto a medio agendar), completa las semanas faltantes
     * con la proyección de CicloClases desde el inicio de ESE ciclo — el ciclo siempre debe
     * mostrar sus 4 fechas. Si el alumno no tiene ninguna clase activa todavía, el "ciclo vigente"
     * es el primero, y esto cae exactamente en la proyección pura desde fechaPrimeraClase, la
     * misma que se le anunció al alumno al capturar la fecha.
     *
     * Antes tomaba siempre las primeras 4 clases activas del alumno (limit(4) desde el inicio),
     * así que un alumno con más de un ciclo ya agendado (2do ciclo en adelante) se quedaba viendo
     * para siempre las fechas de su primer ciclo en vez de las del vigente.
     */
    public List<LocalDate> resolverFechasCiclo(Long alumnoId, LocalDate fechaPrimeraClase) {
        List<Clase> todasActivas = claseRepository.findActivasDesde(alumnoId, ESTADOS_OCUPAN_CUPO,
                fechaPrimeraClase.atStartOfDay());

        LocalDate inicioCicloVigente = todasActivas.isEmpty()
                ? fechaPrimeraClase
                : inicioDelCicloQueContiene(
                        todasActivas.stream().map(c -> c.getFechaHora().toLocalDate())
                                .max(LocalDate::compareTo).orElseThrow(),
                        fechaPrimeraClase);
        LocalDate finCicloVigente = inicioCicloVigente.plusDays(CicloClases.DIAS_POR_CICLO);

        List<LocalDate> delCicloVigente = todasActivas.stream()
                .map(c -> c.getFechaHora().toLocalDate())
                .filter(fecha -> !fecha.isBefore(inicioCicloVigente) && fecha.isBefore(finCicloVigente))
                .toList();

        List<LocalDate> proyectadas = CicloClases.fechasClases(inicioCicloVigente);
        List<LocalDate> resueltas = new ArrayList<>(delCicloVigente);
        for (int i = resueltas.size(); i < CicloClases.CLASES_POR_CICLO; i++) {
            resueltas.add(proyectadas.get(i));
        }
        return resueltas;
    }

    /**
     * Dado que cada ciclo dura exactamente DIAS_POR_CICLO días desde fechaPrimeraClase, ubica en
     * qué ciclo cae "fecha" y regresa el primer día de ESE ciclo. Usado para ubicar la ventana del
     * ciclo vigente en resolverFechasCiclo — nunca se usa para clases anteriores a
     * fechaPrimeraClase.
     */
    private LocalDate inicioDelCicloQueContiene(LocalDate fecha, LocalDate fechaPrimeraClase) {
        long diasDesdeInicio = ChronoUnit.DAYS.between(fechaPrimeraClase, fecha);
        long ciclosCompletos = Math.floorDiv(diasDesdeInicio, CicloClases.DIAS_POR_CICLO);
        return fechaPrimeraClase.plusDays(ciclosCompletos * CicloClases.DIAS_POR_CICLO);
    }

    /**
     * Fecha de arranque del PRÓXIMO ciclo de 4 clases a programar. alumno.fechaPrimeraClase es
     * fija de por vida (se captura una sola vez al inscribir al alumno) y NO se mueve ciclo tras
     * ciclo, así que no sirve como ancla una vez que el primer ciclo ya se agendó — usarla siempre
     * hacía que programarCiclo recalculara las mismas 4 fechas del primer ciclo cada vez que se
     * generaba el segundo, tercero, etc., chocando con las clases reales ya existentes de ciclos
     * anteriores (ConflictoHorarioException falso, no un cruce real de agenda).
     *
     * Si el alumno ya tiene clases activas (no CANCELADA/REAGENDADA), el próximo ciclo arranca 7
     * días después de la más reciente de ellas — sea cual sea su fecha real, incluyendo cualquier
     * reagendo ya aprobado (la clase reagendada reemplaza a la original en este cálculo, igual que
     * en resolverFechasCiclo). Esto es lo que permite que un alumno desfasado de la semana natural
     * del mes (como uno cuya clase se reagendó) siga generando sus ciclos siguientes en la fecha
     * que le corresponde a ÉL, no a un calendario mensual fijo.
     *
     * Si todavía no tiene ninguna clase activa (alumno nuevo, primer ciclo), arranca en
     * fechaPrimeraClase tal cual — mismo comportamiento que siempre.
     */
    private LocalDate fechaInicioProximoCiclo(Long alumnoId, LocalDate fechaPrimeraClase) {
        List<Clase> activas = claseRepository.findActivasDesde(alumnoId, ESTADOS_OCUPAN_CUPO,
                fechaPrimeraClase.atStartOfDay());
        if (activas.isEmpty()) {
            return fechaPrimeraClase;
        }
        LocalDate ultimaFecha = activas.stream()
                .map(c -> c.getFechaHora().toLocalDate())
                .max(LocalDate::compareTo)
                .orElseThrow();
        return ultimaFecha.plusWeeks(1);
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
     * Agenda de un solo golpe las 4 clases semanales del ciclo vigente (ver CicloClases),
     * repartidas entre "asignaciones" (instrumento/profesor/hora, cada una con su cantidad — ver
     * AsignacionCicloItem). Todo o nada: si cualquiera de las 4 choca con un horario ocupado o
     * excede el cupo mensual (por instrumento, si el alumno tiene cupos configurados), no se crea
     * ninguna.
     *
     * El ciclo arranca en fechaPrimeraClase solo la primera vez (alumno sin clases activas
     * todavía); si el alumno ya tiene clases previas, arranca una semana después de la más
     * reciente de ellas — ver fechaInicioProximoCiclo. Así, llamar este método por segunda vez
     * para el mismo alumno genera el SIGUIENTE ciclo (semanas 5-8) en vez de recalcular las
     * mismas 4 fechas del primero.
     */
    @Transactional
    public List<Clase> programarCiclo(Long alumnoId, List<AsignacionCicloItem> asignaciones,
                                       Integer duracionMinutos, String notas) {
        Alumno alumno = alumnoService.obtenerPorId(alumnoId);
        if (alumno.getFechaPrimeraClase() == null) {
            throw new BusinessRuleException("El alumno no tiene registrada su fecha de primera clase");
        }

        int totalSolicitado = asignaciones.stream().mapToInt(AsignacionCicloItem::cantidad).sum();
        if (totalSolicitado != CicloClases.CLASES_POR_CICLO) {
            throw new BusinessRuleException("Las cantidades de las asignaciones deben sumar exactamente "
                    + CicloClases.CLASES_POR_CICLO + " clases (una por cada semana del ciclo), pero suman "
                    + totalSolicitado);
        }

        List<AsignacionCicloItem> expandido = new ArrayList<>();
        for (AsignacionCicloItem asignacion : asignaciones) {
            for (int i = 0; i < asignacion.cantidad(); i++) {
                expandido.add(asignacion);
            }
        }

        int duracion = duracionMinutos != null ? duracionMinutos : appProperties.clases().duracionDefaultMinutos();
        LocalDate inicioCiclo = fechaInicioProximoCiclo(alumnoId, alumno.getFechaPrimeraClase());
        List<LocalDate> fechas = CicloClases.fechasClases(inicioCiclo);

        List<Clase> creadas = new ArrayList<>();
        for (int i = 0; i < fechas.size(); i++) {
            AsignacionCicloItem asignacion = expandido.get(i);
            Profesor profesor = profesorService.obtenerPorId(asignacion.profesorId());
            Clase clase = crearClaseInterna(alumno, profesor, asignacion.instrumento(),
                    fechas.get(i).atTime(asignacion.horaClase()), duracion, null);
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

    /**
     * Borra la clase por completo (a diferencia de cancelar, que la conserva como CANCELADA).
     * Se rechaza si otra fila depende de ella por FK (fue el origen de un reagendo, o tiene una
     * solicitud de reagendación asociada) — ahí hay que usar "Cancelar" en su lugar.
     */
    @Transactional
    public void eliminar(Long claseId) {
        Clase clase = obtenerPorId(claseId);
        if (claseRepository.existsByClaseOriginal_Id(claseId)) {
            throw new BusinessRuleException(
                    "No se puede eliminar: esta clase es el origen de una reagendación. Usa \"Cancelar\" en su lugar.");
        }
        if (solicitudReagendacionRepository.existsByClase_IdOrClaseNueva_Id(claseId, claseId)) {
            throw new BusinessRuleException(
                    "No se puede eliminar: esta clase tiene una solicitud de reagendación asociada. Usa \"Cancelar\" en su lugar.");
        }
        claseRepository.delete(clase);
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
        if (profesorDestino == null) {
            throw new BusinessRuleException(
                    "Esta clase no tiene profesor asignado (su profesor fue eliminado): elige uno para reagendarla");
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
        verificarCupoMensual(alumno.getId(), instrumento, fechaHora, excludeId);

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
                boolean esProfesor = candidata.getProfesor() != null && candidata.getProfesor().getId().equals(profesorId);
                throw new ConflictoHorarioException(
                        "Ya existe una clase que se cruza en el horario solicitado para "
                                + (esProfesor ? "el profesor" : "el alumno"));
            }
        }
    }

    /**
     * Si el alumno tiene cupos particulares por instrumento configurados (AlumnoInstrumentoCupo),
     * valida solo contra el cupo de ESE instrumento (y exige que exista un cupo configurado para
     * él). Si no tiene ninguno configurado, cae al límite mensual global de siempre, contando
     * todos los instrumentos juntos — sin cambio de comportamiento para la mayoría de los alumnos.
     */
    private void verificarCupoMensual(Long alumnoId, Instrumento instrumento, LocalDateTime fechaHora, Long excludeClaseId) {
        LocalDateTime inicioMes = fechaHora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = inicioMes.plusMonths(1);

        List<AlumnoInstrumentoCupo> cupos = cupoRepository.findByAlumno_IdOrderByInstrumento(alumnoId);
        if (cupos.isEmpty()) {
            long ocupadas = claseRepository.countOcupadasEnRango(alumnoId, ESTADOS_OCUPAN_CUPO, inicioMes, finMes, excludeClaseId);
            int limite = appProperties.clases().limiteMensual();
            if (ocupadas >= limite) {
                throw new LimiteMensualExcedidoException(
                        "El alumno ya alcanzó el límite de " + limite + " clases para este mes");
            }
            return;
        }

        AlumnoInstrumentoCupo cupo = cupos.stream()
                .filter(c -> c.getInstrumento() == instrumento)
                .findFirst()
                .orElseThrow(() -> new LimiteMensualExcedidoException(
                        "El alumno no tiene cupo configurado para " + instrumento));
        long ocupadasInstrumento = claseRepository.countOcupadasEnRangoPorInstrumento(
                alumnoId, instrumento, ESTADOS_OCUPAN_CUPO, inicioMes, finMes, excludeClaseId);
        if (ocupadasInstrumento >= cupo.getCupoMensual()) {
            throw new LimiteMensualExcedidoException(
                    "El alumno ya alcanzó su límite de " + cupo.getCupoMensual() + " clases de "
                            + instrumento + " para este mes");
        }
    }
}

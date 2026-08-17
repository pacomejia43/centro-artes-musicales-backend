package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.config.AppProperties;
import com.centroartesmusicales.backend.dto.pago.CrearPagoRequest;
import com.centroartesmusicales.backend.dto.pago.RegistrarTransaccionRequest;
import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.EstadoPago;
import com.centroartesmusicales.backend.model.EstadoTransaccion;
import com.centroartesmusicales.backend.model.Pago;
import com.centroartesmusicales.backend.model.PagoTransaccion;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.PagoRepository;
import com.centroartesmusicales.backend.repository.PagoTransaccionRepository;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository pagoRepository;
    private final PagoTransaccionRepository pagoTransaccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlumnoService alumnoService;
    private final AppProperties appProperties;

    private ZoneId zoneId() {
        return ZoneId.of(appProperties.timezone());
    }

    // ---------------------------------------------------------------- lectura

    public Pago obtenerPorId(Long id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado: " + id));
    }

    public Pago obtenerPropio(Long usuarioId, Long pagoId) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        Pago pago = obtenerPorId(pagoId);
        if (!pago.getAlumno().getId().equals(alumno.getId())) {
            throw new ResourceNotFoundException("Pago no encontrado: " + pagoId);
        }
        return pago;
    }

    public Page<Pago> listar(Long alumnoId, EstadoPago estado, YearMonth periodo, Pageable pageable) {
        return pagoRepository.buscar(alumnoId, estado, periodo, pageable);
    }

    public Page<Pago> listarPropios(Long usuarioId, Pageable pageable) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        return pagoRepository.buscar(alumno.getId(), null, null, pageable);
    }

    /** Current-month pago if one exists, otherwise the most recent one on record. */
    public Pago obtenerActualPropio(Long usuarioId) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        YearMonth mesActual = YearMonth.now(zoneId());
        return pagoRepository.findByAlumno_IdAndPeriodo(alumno.getId(), mesActual)
                .or(() -> pagoRepository.findFirstByAlumno_IdOrderByPeriodoDesc(alumno.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("El alumno no tiene pagos registrados"));
    }

    public BigDecimal montoPagado(Pago pago) {
        return pagoTransaccionRepository.sumConfirmadoByPagoId(pago.getId());
    }

    public boolean esVencido(Pago pago) {
        return pago.getEstado() != EstadoPago.PAGADO && pago.getFechaLimite().isBefore(LocalDate.now(zoneId()));
    }

    // ---------------------------------------------------------------- escritura (admin)

    @Transactional
    public Pago crearCargo(Long alumnoId, CrearPagoRequest request) {
        Alumno alumno = alumnoService.obtenerPorId(alumnoId);
        YearMonth periodo = request.periodo() != null ? request.periodo() : YearMonth.now(zoneId());
        BigDecimal monto = request.monto() != null ? request.monto() : appProperties.pagos().montoMensualDefault();

        if (pagoRepository.existsByAlumno_IdAndPeriodo(alumnoId, periodo)) {
            throw new BusinessRuleException("Ya existe un cargo registrado para el alumno en el período " + periodo);
        }

        Pago pago = Pago.builder()
                .alumno(alumno)
                .monto(monto)
                .periodo(periodo)
                .fechaLimite(request.fechaLimite())
                .notas(request.notas())
                .estado(EstadoPago.PENDIENTE)
                .build();
        return pagoRepository.save(pago);
    }

    /** Admin-entered payments are auto-confirmed — the admin action IS the confirmation. */
    @Transactional
    public PagoTransaccion registrarTransaccionAdmin(Long pagoId, Long adminUsuarioId, RegistrarTransaccionRequest request) {
        Pago pago = obtenerPorId(pagoId);
        Usuario admin = usuarioRepository.getReferenceById(adminUsuarioId);
        LocalDateTime ahora = LocalDateTime.now(zoneId());

        PagoTransaccion transaccion = PagoTransaccion.builder()
                .pago(pago)
                .monto(request.monto())
                .fecha(request.fecha() != null ? request.fecha() : ahora)
                .metodoPago(request.metodoPago())
                .referencia(request.referencia())
                .estado(EstadoTransaccion.CONFIRMADA)
                .revisadoPor(admin)
                .revisadoAt(ahora)
                .registradoPor(admin)
                .build();
        transaccion = pagoTransaccionRepository.save(transaccion);

        recalcularEstado(pago);
        return transaccion;
    }

    /** Self-reported payments start unconfirmed and don't move the balance until an admin reviews them. */
    @Transactional
    public PagoTransaccion autorreportarPago(Long usuarioId, Long pagoId, RegistrarTransaccionRequest request) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        Pago pago = obtenerPorId(pagoId);
        if (!pago.getAlumno().getId().equals(alumno.getId())) {
            throw new ResourceNotFoundException("Pago no encontrado: " + pagoId);
        }
        Usuario usuario = usuarioRepository.getReferenceById(usuarioId);

        PagoTransaccion transaccion = PagoTransaccion.builder()
                .pago(pago)
                .monto(request.monto())
                .fecha(request.fecha() != null ? request.fecha() : LocalDateTime.now(zoneId()))
                .metodoPago(request.metodoPago())
                .referencia(request.referencia())
                .estado(EstadoTransaccion.PENDIENTE)
                .registradoPor(usuario)
                .build();
        return pagoTransaccionRepository.save(transaccion);
    }

    @Transactional
    public PagoTransaccion confirmarTransaccion(Long transaccionId, Long adminUsuarioId) {
        PagoTransaccion transaccion = obtenerTransaccion(transaccionId);
        if (transaccion.getEstado() != EstadoTransaccion.PENDIENTE) {
            throw new BusinessRuleException("La transacción ya fue revisada");
        }
        Usuario admin = usuarioRepository.getReferenceById(adminUsuarioId);
        transaccion.setEstado(EstadoTransaccion.CONFIRMADA);
        transaccion.setRevisadoPor(admin);
        transaccion.setRevisadoAt(LocalDateTime.now(zoneId()));
        transaccion = pagoTransaccionRepository.save(transaccion);

        recalcularEstado(transaccion.getPago());
        return transaccion;
    }

    @Transactional
    public PagoTransaccion rechazarTransaccion(Long transaccionId, Long adminUsuarioId, String motivoRechazo) {
        PagoTransaccion transaccion = obtenerTransaccion(transaccionId);
        if (transaccion.getEstado() != EstadoTransaccion.PENDIENTE) {
            throw new BusinessRuleException("La transacción ya fue revisada");
        }
        Usuario admin = usuarioRepository.getReferenceById(adminUsuarioId);
        transaccion.setEstado(EstadoTransaccion.RECHAZADA);
        transaccion.setMotivoRechazo(motivoRechazo);
        transaccion.setRevisadoPor(admin);
        transaccion.setRevisadoAt(LocalDateTime.now(zoneId()));
        // No recalcularEstado: a PENDIENTE transaction never affected the balance.
        return pagoTransaccionRepository.save(transaccion);
    }

    private PagoTransaccion obtenerTransaccion(Long id) {
        return pagoTransaccionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción no encontrada: " + id));
    }

    private void recalcularEstado(Pago pago) {
        BigDecimal pagado = montoPagado(pago);
        if (pagado.compareTo(BigDecimal.ZERO) <= 0) {
            pago.setEstado(EstadoPago.PENDIENTE);
        } else if (pagado.compareTo(pago.getMonto()) >= 0) {
            pago.setEstado(EstadoPago.PAGADO);
        } else {
            pago.setEstado(EstadoPago.PARCIAL);
        }
        pagoRepository.save(pago);
    }
}

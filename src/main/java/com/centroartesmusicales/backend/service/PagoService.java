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
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.PagoRepository;
import com.centroartesmusicales.backend.repository.PagoTransaccionRepository;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
import com.centroartesmusicales.backend.util.CicloClases;
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
import java.util.Optional;

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
        BigDecimal monto = request.monto() != null ? request.monto() : montoMensual(alumno);

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

    /**
     * Crea el cargo del ciclo de 4 clases (ver CicloClases) si todavía no existe uno para esa
     * fecha límite u ese período — se llama cada vez que se guarda fechaPrimeraClase del alumno,
     * así que debe ser idempotente en vez de asumir que es la primera vez que se invoca.
     */
    @Transactional
    public Optional<Pago> crearCargoCicloSiNoExiste(Long alumnoId, LocalDate fechaPrimeraClase) {
        LocalDate fechaLimite = CicloClases.proximoPago(fechaPrimeraClase);
        if (pagoRepository.existsByAlumno_IdAndFechaLimite(alumnoId, fechaLimite)) {
            return Optional.empty();
        }
        YearMonth periodo = YearMonth.from(fechaLimite);
        if (pagoRepository.existsByAlumno_IdAndPeriodo(alumnoId, periodo)) {
            return Optional.empty();
        }

        Alumno alumno = alumnoService.obtenerPorId(alumnoId);
        Pago pago = Pago.builder()
                .alumno(alumno)
                .monto(montoMensual(alumno))
                .periodo(periodo)
                .fechaLimite(fechaLimite)
                .notas("Generado automáticamente: ciclo de 4 clases desde " + fechaPrimeraClase)
                .estado(EstadoPago.PENDIENTE)
                .build();
        return Optional.of(pagoRepository.save(pago));
    }

    /**
     * Edita un cargo ya existente — típicamente para corregir el período cuando un pago quedó
     * registrado en el mes equivocado (ej. correspondía al mes corriente o al que está por
     * comenzar). Campos nulos no se tocan, igual que AlumnoService#actualizar. Si el período
     * cambia, se recalcula el estado por si el monto también cambió.
     */
    @Transactional
    public Pago actualizarCargo(Long pagoId, ActualizarPagoRequest request) {
        Pago pago = obtenerPorId(pagoId);

        if (request.periodo() != null && !request.periodo().equals(pago.getPeriodo())) {
            if (pagoRepository.existsByAlumno_IdAndPeriodo(pago.getAlumno().getId(), request.periodo())) {
                throw new BusinessRuleException(
                        "Ya existe un cargo registrado para el alumno en el período " + request.periodo());
            }
            pago.setPeriodo(request.periodo());
        }
        if (request.monto() != null) {
            pago.setMonto(request.monto());
        }
        if (request.fechaLimite() != null) {
            pago.setFechaLimite(request.fechaLimite());
        }
        if (request.notas() != null) {
            pago.setNotas(request.notas());
        }

        recalcularEstado(pago);
        return pago;
    }

    /** Precio particular del alumno si el admin se lo asignó; si no, el default global. Público
     *  porque StripeCheckoutService/SuscripcionService también lo necesitan — es la única fuente
     *  de verdad de "cuánto paga este alumno al mes", nunca se duplica este cálculo. */
    public BigDecimal montoMensual(Alumno alumno) {
        return alumno.getPrecioMensual() != null ? alumno.getPrecioMensual() : appProperties.pagos().montoMensualDefault();
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

    /**
     * Borra una transacción por completo — a diferencia de rechazar (que la conserva como
     * RECHAZADA), esto es para cuando el admin confirmó una por error y quiere que desaparezca
     * del historial, no solo que quede marcada. Funciona en cualquier estado; si estaba
     * CONFIRMADA, se recalcula el saldo/estado del pago después de quitarla de la suma.
     */
    @Transactional
    public void eliminarTransaccion(Long transaccionId) {
        PagoTransaccion transaccion = obtenerTransaccion(transaccionId);
        Pago pago = transaccion.getPago();
        pagoTransaccionRepository.delete(transaccion);
        pagoTransaccionRepository.flush();
        recalcularEstado(pago);
    }

    private PagoTransaccion obtenerTransaccion(Long id) {
        return pagoTransaccionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción no encontrada: " + id));
    }

    private void recalcularEstado(Pago pago) {
        aplicarEstadoSegunSaldo(pago, EstadoPago.PENDIENTE);
    }

    /** estadoSiSinPago es PENDIENTE en el camino normal, pero REEMBOLSADO cuando esta
     *  recalculación se dispara porque se acaba de reembolsar la transacción que cubría el saldo
     *  (ver marcarReembolsoPorPaymentIntent) — un pago reembolsado no debe verse igual que uno que
     *  simplemente nunca se pagó. */
    private void aplicarEstadoSegunSaldo(Pago pago, EstadoPago estadoSiSinPago) {
        BigDecimal pagado = montoPagado(pago);
        if (pagado.compareTo(BigDecimal.ZERO) <= 0) {
            pago.setEstado(estadoSiSinPago);
        } else if (pagado.compareTo(pago.getMonto()) >= 0) {
            pago.setEstado(EstadoPago.PAGADO);
        } else {
            pago.setEstado(EstadoPago.PARCIAL);
        }
        pagoRepository.save(pago);
    }

    // ---------------------------------------------------------------- Stripe

    /** Datos ya validados que trae un evento de Stripe — StripeWebhookService los arma a partir
     *  del Event verificado, nunca de nada que haya mandado el frontend. */
    public record DatosTransaccionStripe(
            BigDecimal monto,
            String stripePaymentIntentId,
            String stripeCheckoutSessionId,
            String stripeInvoiceId,
            String stripeSubscriptionId
    ) {
    }

    /**
     * Idempotente por (alumno, periodo): invoice.paid de un cobro recurrente puede necesitar un
     * Pago del mes que todavía no existía en nuestra BD (el ciclo de Stripe Billing avanza solo).
     * Si ya existe —por ejemplo porque el admin ya lo había creado a mano— se reutiliza tal cual,
     * incluyendo su monto ya definido, en vez de pisarlo.
     */
    @Transactional
    public Pago crearOEncontrarCargoParaPeriodo(Long alumnoId, YearMonth periodo, BigDecimal monto) {
        return pagoRepository.findByAlumno_IdAndPeriodo(alumnoId, periodo)
                .orElseGet(() -> {
                    Alumno alumno = alumnoService.obtenerPorId(alumnoId);
                    Pago pago = Pago.builder()
                            .alumno(alumno)
                            .monto(monto)
                            .periodo(periodo)
                            .fechaLimite(periodo.atEndOfMonth())
                            .notas("Generado automáticamente: cobro recurrente de Stripe")
                            .estado(EstadoPago.PENDIENTE)
                            .build();
                    return pagoRepository.save(pago);
                });
    }

    /** Análogo a registrarTransaccionAdmin, pero la "confirmación" la dio el webhook de Stripe en
     *  vez de un admin — por eso registradoPor es el propio alumno y revisadoPor queda vacío (no
     *  hubo revisión humana). */
    @Transactional
    public PagoTransaccion registrarTransaccionStripe(Long pagoId, DatosTransaccionStripe datos) {
        Pago pago = obtenerPorId(pagoId);
        Usuario alumnoUsuario = pago.getAlumno().getUsuario();
        LocalDateTime ahora = LocalDateTime.now(zoneId());

        PagoTransaccion transaccion = PagoTransaccion.builder()
                .pago(pago)
                .monto(datos.monto())
                .fecha(ahora)
                .metodoPago(MetodoPago.STRIPE)
                .referencia(referenciaStripe(datos))
                .estado(EstadoTransaccion.CONFIRMADA)
                .registradoPor(alumnoUsuario)
                .revisadoAt(ahora)
                .stripePaymentIntentId(datos.stripePaymentIntentId())
                .stripeCheckoutSessionId(datos.stripeCheckoutSessionId())
                .stripeInvoiceId(datos.stripeInvoiceId())
                .stripeSubscriptionId(datos.stripeSubscriptionId())
                .build();
        transaccion = pagoTransaccionRepository.save(transaccion);

        recalcularEstado(pago);
        return transaccion;
    }

    /** payment_intent.payment_failed / invoice.payment_failed: se deja constancia del intento
     *  fallido, pero —igual que una transacción RECHAZADA— nunca mueve el saldo del Pago. */
    @Transactional
    public PagoTransaccion registrarTransaccionFallida(Long pagoId, DatosTransaccionStripe datos) {
        Pago pago = obtenerPorId(pagoId);
        Usuario alumnoUsuario = pago.getAlumno().getUsuario();

        PagoTransaccion transaccion = PagoTransaccion.builder()
                .pago(pago)
                .monto(datos.monto())
                .fecha(LocalDateTime.now(zoneId()))
                .metodoPago(MetodoPago.STRIPE)
                .referencia(referenciaStripe(datos))
                .estado(EstadoTransaccion.FALLIDA)
                .registradoPor(alumnoUsuario)
                .stripePaymentIntentId(datos.stripePaymentIntentId())
                .stripeCheckoutSessionId(datos.stripeCheckoutSessionId())
                .stripeInvoiceId(datos.stripeInvoiceId())
                .stripeSubscriptionId(datos.stripeSubscriptionId())
                .build();
        return pagoTransaccionRepository.save(transaccion);
    }

    /** charge.refunded: busca la transacción original por payment_intent (así es como llega el
     *  evento de Stripe) y la marca REEMBOLSADA. Devuelve vacío si no encuentra ninguna
     *  transacción nuestra con ese payment_intent — puede pasar con un reembolso de un cargo que
     *  no vino de este sistema; en ese caso no hay nada que reconciliar de nuestro lado. */
    @Transactional
    public Optional<PagoTransaccion> marcarReembolsoPorPaymentIntent(String stripePaymentIntentId) {
        return pagoTransaccionRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .map(transaccion -> {
                    transaccion.setEstado(EstadoTransaccion.REEMBOLSADA);
                    transaccion = pagoTransaccionRepository.save(transaccion);
                    aplicarEstadoSegunSaldo(transaccion.getPago(), EstadoPago.REEMBOLSADO);
                    return transaccion;
                });
    }

    private String referenciaStripe(DatosTransaccionStripe datos) {
        if (datos.stripeCheckoutSessionId() != null) {
            return datos.stripeCheckoutSessionId();
        }
        return datos.stripeInvoiceId();
    }
}

package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.Pago;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Arma la Checkout Session de un cargo puntual. El monto SIEMPRE sale de Pago/PagoTransaccion en
 * nuestra BD (nunca del cliente) — este servicio ni siquiera acepta un monto como parámetro, solo
 * el id del Pago, precisamente para que sea imposible pasarle uno por error.
 */
@Service
@RequiredArgsConstructor
public class StripeCheckoutService {

    private final PagoService pagoService;
    private final AlumnoService alumnoService;
    private final StripeService stripeService;

    @Transactional
    public Session crearCheckoutParaCargo(Long usuarioId, Long pagoId) throws StripeException {
        // obtenerPropio ya lanza ResourceNotFoundException si el cargo no es de este alumno.
        Pago pago = pagoService.obtenerPropio(usuarioId, pagoId);
        Alumno alumno = pago.getAlumno();

        BigDecimal saldoPendiente = pago.getMonto().subtract(pagoService.montoPagado(pago));
        if (saldoPendiente.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Este cargo ya está pagado.");
        }

        String customerId = obtenerOCrearCustomerId(alumno);
        String priceId = stripeService.obtenerOCrearPriceUnico(saldoPendiente);
        return stripeService.crearCheckoutSessionPagoUnico(customerId, priceId, alumno, pago);
    }

    private String obtenerOCrearCustomerId(Alumno alumno) throws StripeException {
        if (alumno.getStripeCustomerId() != null && !alumno.getStripeCustomerId().isBlank()) {
            return alumno.getStripeCustomerId();
        }
        String customerId = stripeService.crearCustomer(alumno);
        alumnoService.guardarStripeCustomerId(alumno.getId(), customerId);
        return customerId;
    }
}

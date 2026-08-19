package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.exception.BusinessRuleException;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.EstadoSuscripcion;
import com.centroartesmusicales.backend.model.Suscripcion;
import com.centroartesmusicales.backend.repository.SuscripcionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Dueño de la fila Suscripcion (a lo sumo una por alumno). Nunca marca una suscripción como
 * activa por su cuenta: iniciarActivacion solo abre el Checkout Session de Stripe — la fila real
 * la crea/actualiza StripeWebhookService cuando llega customer.subscription.created/updated,
 * igual que un pago nunca se confirma por el regreso del navegador a success_url.
 */
@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private final SuscripcionRepository suscripcionRepository;
    private final AlumnoService alumnoService;
    private final PagoService pagoService;
    private final StripeService stripeService;

    public Optional<Suscripcion> obtenerPropia(Long usuarioId) {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        return suscripcionRepository.findByAlumno_Id(alumno.getId());
    }

    @Transactional
    public Session iniciarActivacion(Long usuarioId) throws StripeException {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);

        suscripcionRepository.findByAlumno_Id(alumno.getId()).ifPresent(actual -> {
            if (actual.getEstado() == EstadoSuscripcion.ACTIVA && !actual.isCancelacionProgramada()) {
                throw new BusinessRuleException("Ya tienes un pago automático activo.");
            }
        });

        String customerId = obtenerOCrearCustomerId(alumno);
        BigDecimal monto = pagoService.montoMensual(alumno);
        String priceId = stripeService.obtenerOCrearPriceRecurrente(monto);
        return stripeService.crearCheckoutSessionSuscripcion(customerId, priceId, alumno);
    }

    /**
     * Programa la cancelación para el final del periodo ya pagado (ver StripeService#cancelarSuscripcion)
     * — Stripe seguirá reportando la suscripción como ACTIVA hasta entonces, así que solo marcamos
     * cancelacionProgramada aquí; el estado final CANCELADA llega por customer.subscription.deleted.
     */
    @Transactional
    public void cancelar(Long usuarioId) throws StripeException {
        Alumno alumno = alumnoService.obtenerPorUsuarioId(usuarioId);
        Suscripcion suscripcion = suscripcionRepository.findByAlumno_Id(alumno.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No tienes un pago automático activo."));
        if (suscripcion.getEstado() == EstadoSuscripcion.CANCELADA) {
            throw new BusinessRuleException("Tu pago automático ya está cancelado.");
        }
        if (suscripcion.isCancelacionProgramada()) {
            throw new BusinessRuleException("La cancelación de tu pago automático ya está en trámite.");
        }
        stripeService.cancelarSuscripcion(suscripcion.getStripeSubscriptionId());
        suscripcion.setCancelacionProgramada(true);
        suscripcionRepository.save(suscripcion);
    }

    private String obtenerOCrearCustomerId(Alumno alumno) throws StripeException {
        if (alumno.getStripeCustomerId() != null && !alumno.getStripeCustomerId().isBlank()) {
            return alumno.getStripeCustomerId();
        }
        String customerId = stripeService.crearCustomer(alumno);
        alumnoService.guardarStripeCustomerId(alumno.getId(), customerId);
        alumno.setStripeCustomerId(customerId);
        return customerId;
    }
}

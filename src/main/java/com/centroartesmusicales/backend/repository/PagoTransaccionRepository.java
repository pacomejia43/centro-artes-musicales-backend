package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.PagoTransaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PagoTransaccionRepository extends JpaRepository<PagoTransaccion, Long> {

    @Query("SELECT COALESCE(SUM(t.monto), 0) FROM PagoTransaccion t "
            + "WHERE t.pago.id = :pagoId AND t.estado = com.centroartesmusicales.backend.model.EstadoTransaccion.CONFIRMADA")
    BigDecimal sumConfirmadoByPagoId(@Param("pagoId") Long pagoId);

    /** Borrado en cascada al eliminar un alumno por completo (ver AlumnoService#eliminar). */
    @Modifying
    @Query("DELETE FROM PagoTransaccion t WHERE t.pago.alumno.id = :alumnoId")
    void deleteByPagoAlumnoId(@Param("alumnoId") Long alumnoId);

    /** Usado por el polling del frontend tras volver de Stripe Checkout (ver PagoSelfController)
     *  para saber si el webhook ya confirmó el pago o todavía está en camino. */
    Optional<PagoTransaccion> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    /** Usado al procesar charge.refunded, que solo trae el payment_intent en el payload. */
    Optional<PagoTransaccion> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<PagoTransaccion> findByStripeInvoiceId(String stripeInvoiceId);
}

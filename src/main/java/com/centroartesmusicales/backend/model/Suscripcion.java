package com.centroartesmusicales.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pago automático (Stripe Billing) de la mensualidad de un alumno. A lo sumo una fila por alumno
 * (alumno_id es UNIQUE): si cancela y vuelve a activar, se reutiliza la misma fila con un
 * stripe_subscription_id nuevo en vez de acumular históricas — el historial de cobros ya vive en
 * PagoTransaccion, esta tabla solo representa "¿tiene pago automático activo ahora mismo?".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, of = "id")
@Entity
@Table(name = "suscripcion")
public class Suscripcion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = false, unique = true)
    private Alumno alumno;

    @Column(name = "stripe_subscription_id", nullable = false, length = 255, unique = true)
    private String stripeSubscriptionId;

    @Column(name = "stripe_price_id", nullable = false, length = 255)
    private String stripePriceId;

    @Column(name = "stripe_customer_id", nullable = false, length = 255)
    private String stripeCustomerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSuscripcion estado;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    /** true entre que el alumno pide cancelar y el fin del periodo ya pagado (Stripe sigue
     *  reportando la suscripción como activa hasta entonces vía cancel_at_period_end). */
    @Column(name = "cancelacion_programada", nullable = false)
    @Builder.Default
    private boolean cancelacionProgramada = false;

    @Column(name = "fecha_cancelacion")
    private LocalDate fechaCancelacion;
}

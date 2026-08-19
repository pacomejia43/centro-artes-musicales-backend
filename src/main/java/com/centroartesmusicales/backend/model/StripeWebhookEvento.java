package com.centroartesmusicales.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un registro por cada Stripe Event ya procesado (id nativo de Stripe, ej. "evt_..."). Stripe
 * puede reintentar la entrega del mismo evento; stripe_event_id es UNIQUE, así que un segundo
 * intento choca con una restricción de BD y StripeWebhookService lo trata como no-op en vez de
 * volver a aplicar sus efectos (ver StripeWebhookService#yaProcesado).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, of = "id")
@Entity
@Table(name = "stripe_webhook_evento")
public class StripeWebhookEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_event_id", nullable = false, length = 255, unique = true)
    private String stripeEventId;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(name = "procesado_at", nullable = false)
    private LocalDateTime procesadoAt;
}

package com.centroartesmusicales.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cachea el Stripe Price (y su Product) ya creado para un monto+tipo dado, para que dos alumnos
 * con el mismo precio mensual —o el mismo alumno pagando dos meses distintos— reutilicen el
 * mismo Price en vez de que StripeService cree uno nuevo cada vez (ver StripeService#obtenerOCrearPrice).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, of = "id")
@Entity
@Table(name = "stripe_price_cache", uniqueConstraints = @UniqueConstraint(columnNames = {"monto", "tipo"}))
public class StripePriceCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPrecioStripe tipo;

    @Column(name = "stripe_product_id", nullable = false, length = 255)
    private String stripeProductId;

    @Column(name = "stripe_price_id", nullable = false, length = 255)
    private String stripePriceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

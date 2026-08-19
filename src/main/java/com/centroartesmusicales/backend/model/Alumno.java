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
 * Student profile. Deliberately unidirectional: Usuario does NOT map back to Alumno (see plan) —
 * anything holding a Usuario/principal looks up its profile via AlumnoRepository#findByUsuarioId.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, of = "id")
@Entity
@Table(name = "alumno")
public class Alumno extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(length = 30)
    private String telefono;

    private LocalDate fechaNacimiento;

    @Column(nullable = false)
    private LocalDate fechaInscripcion;

    /** Fecha de la primera clase del ciclo vigente (ver util.CicloClases) — la captura el admin. */
    private LocalDate fechaPrimeraClase;

    /** Precio mensual particular de este alumno (algunos pagan distinto). Si es NULL, se usa
     *  app.pagos.monto-mensual-default — ver PagoService. */
    @Column(name = "precio_mensual", precision = 10, scale = 2)
    private BigDecimal precioMensual;

    /** Id del Customer de Stripe asociado a este alumno. Null hasta el primer checkout o
     *  suscripción — se crea de forma perezosa (ver StripeService#obtenerOCrearCustomer) para no
     *  generar Customers en Stripe que nunca lleguen a pagar nada. */
    @Column(name = "stripe_customer_id", length = 255, unique = true)
    private String stripeCustomerId;

    @Column(name = "google_docs_url_1", length = 500)
    private String googleDocsUrl1;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrumento_bitacora_1", length = 40)
    private Instrumento instrumentoBitacora1;

    @Column(name = "google_docs_url_2", length = 500)
    private String googleDocsUrl2;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrumento_bitacora_2", length = 40)
    private Instrumento instrumentoBitacora2;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Self-service reschedule request from an alumno. Creating one does NOT move the class —
 * only an admin approval (ClaseService#aprobarSolicitud) executes the actual reschedule.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, of = "id")
@Entity
@Table(name = "solicitud_reagendacion")
public class SolicitudReagendacion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clase_id", nullable = false)
    private Clase clase;

    @Column(nullable = false)
    private LocalDateTime fechaHoraPropuesta;

    /** Null = keep the same profesor as the original clase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profesor_propuesto_id")
    private Profesor profesorPropuesto;

    @Column(length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado;

    @Column(length = 500)
    private String motivoRechazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por")
    private Usuario revisadoPor;

    private LocalDateTime revisadoAt;

    /** Set once approved — the new Clase row created by the reschedule. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clase_nueva_id")
    private Clase claseNueva;
}

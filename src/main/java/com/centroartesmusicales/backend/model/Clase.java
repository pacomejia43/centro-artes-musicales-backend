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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A single scheduled lesson occurrence (this school does 1-on-1 lessons, not recurring group
 * courses). Rows are never mutated into a different time slot — rescheduling marks this row
 * REAGENDADA and creates a new row pointing back via claseOriginal, preserving full history.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, of = "id")
@Entity
@Table(name = "clase")
public class Clase extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    /** Null cuando el profesor que la impartía fue eliminado y no había otro al cual reasignarla
     *  (ver ProfesorService#eliminar) — queda a la espera de que el admin le asigne uno nuevo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profesor_id")
    private Profesor profesor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Instrumento instrumento;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(nullable = false)
    private Integer duracionMinutos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoClase estado;

    /** Points backward to the class this row replaced, when it was created via a reschedule. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clase_original_id")
    private Clase claseOriginal;

    @Column(length = 1000)
    private String notas;

    @Version
    private Long version;
}

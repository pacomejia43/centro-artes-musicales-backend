package com.centroartesmusicales.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "google_docs_url", length = 500)
    private String googleDocsUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

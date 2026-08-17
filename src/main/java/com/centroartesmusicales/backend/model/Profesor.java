package com.centroartesmusicales.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, of = "id")
@Entity
@Table(name = "profesor")
public class Profesor extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String nombreCompleto;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String telefono;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profesor_especialidad", joinColumns = @JoinColumn(name = "profesor_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "instrumento", length = 40)
    @Builder.Default
    private Set<Instrumento> especialidades = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}

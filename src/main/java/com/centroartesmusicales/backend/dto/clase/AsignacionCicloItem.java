package com.centroartesmusicales.backend.dto.clase;

import com.centroartesmusicales.backend.model.Instrumento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;

/**
 * Una porción del ciclo de 4 clases: "cantidad" clases de "instrumento", con ese profesor y hora.
 * Permite ciclos mixtos (ej. Valentina: 2 de piano con un profesor + 2 de canto con otro) — antes
 * ProgramarCicloClasesRequest solo aceptaba un instrumento/profesor/hora únicos para las 4 clases.
 */
public record AsignacionCicloItem(
        @NotNull Instrumento instrumento,
        @NotNull Long profesorId,
        @NotNull LocalTime horaClase,
        @NotNull @Positive Integer cantidad
) {
}

package com.centroartesmusicales.backend.dto.clase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Agenda de un solo golpe las 4 clases semanales del ciclo vigente del alumno (ver
 * util.CicloClases), a partir de su fechaPrimeraClase ya registrada. "asignaciones" reparte esas
 * 4 clases entre instrumentos/profesores/horas — para el caso común (un solo instrumento) basta
 * una asignación con cantidad 4; para alumnos con cupos mixtos (ej. 2 de piano + 2 de canto),
 * una asignación por instrumento. La suma de las cantidades debe dar exactamente 4.
 */
public record ProgramarCicloClasesRequest(
        @NotNull Long alumnoId,
        @NotEmpty @Valid List<AsignacionCicloItem> asignaciones,
        @Positive Integer duracionMinutos,
        String notas
) {
}

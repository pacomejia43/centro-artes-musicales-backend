package com.centroartesmusicales.backend.dto.clase;

import com.centroartesmusicales.backend.model.Instrumento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;

/**
 * Agenda de un solo golpe las 4 clases semanales del ciclo vigente del alumno (ver
 * util.CicloClases), a partir de su fechaPrimeraClase ya registrada — por eso no repite la fecha
 * de inicio aquí, solo los datos que esa fecha por sí sola no trae: profesor, instrumento y hora.
 */
public record ProgramarCicloClasesRequest(
        @NotNull Long alumnoId,
        @NotNull Long profesorId,
        @NotNull Instrumento instrumento,
        @NotNull LocalTime horaClase,
        @Positive Integer duracionMinutos,
        String notas
) {
}

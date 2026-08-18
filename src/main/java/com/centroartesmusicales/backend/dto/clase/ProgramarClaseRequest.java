package com.centroartesmusicales.backend.dto.clase;

import com.centroartesmusicales.backend.model.Instrumento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

/**
 * fechaHora acepta pasado, presente o futuro a propósito: el admin necesita poder registrar
 * clases que ya ocurrieron (ej. dio de alta al alumno una semana después de su primera clase
 * real). Para agendar hacia adelante con el aviso de conflicto/cupo normal simplemente se manda
 * una fecha futura; nada cambia en ese caso.
 */
public record ProgramarClaseRequest(
        @NotNull Long alumnoId,
        @NotNull Long profesorId,
        @NotNull Instrumento instrumento,
        @NotNull LocalDateTime fechaHora,
        @Positive Integer duracionMinutos,
        String notas
) {
}

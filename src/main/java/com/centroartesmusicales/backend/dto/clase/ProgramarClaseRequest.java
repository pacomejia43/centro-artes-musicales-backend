package com.centroartesmusicales.backend.dto.clase;

import com.centroartesmusicales.backend.model.Instrumento;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record ProgramarClaseRequest(
        @NotNull Long alumnoId,
        @NotNull Long profesorId,
        @NotNull Instrumento instrumento,
        @NotNull @Future LocalDateTime fechaHora,
        @Positive Integer duracionMinutos,
        String notas
) {
}

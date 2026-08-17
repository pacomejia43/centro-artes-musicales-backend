package com.centroartesmusicales.backend.dto.clase;

import com.centroartesmusicales.backend.model.EstadoClase;
import com.centroartesmusicales.backend.model.Instrumento;

import java.time.LocalDateTime;

public record ClaseResponse(
        Long id,
        Long alumnoId,
        String alumnoNombre,
        Long profesorId,
        String profesorNombre,
        Instrumento instrumento,
        LocalDateTime fechaHora,
        Integer duracionMinutos,
        EstadoClase estado,
        Long claseOriginalId,
        String notas
) {
}

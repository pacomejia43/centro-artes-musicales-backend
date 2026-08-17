package com.centroartesmusicales.backend.dto.alumno;

import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record ActualizarAlumnoRequest(
        String nombre,
        String telefono,
        @Past LocalDate fechaNacimiento,
        String googleDocsUrl,
        Boolean activo
) {
}

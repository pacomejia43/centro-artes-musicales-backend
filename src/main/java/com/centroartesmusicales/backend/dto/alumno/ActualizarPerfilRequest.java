package com.centroartesmusicales.backend.dto.alumno;

import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * Self-service profile update — deliberately excludes email/role/googleDocsUrl/activo,
 * which only admin can change.
 */
public record ActualizarPerfilRequest(
        String nombre,
        String telefono,
        @Past LocalDate fechaNacimiento
) {
}

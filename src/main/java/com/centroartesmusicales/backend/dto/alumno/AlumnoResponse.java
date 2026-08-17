package com.centroartesmusicales.backend.dto.alumno;

import java.time.LocalDate;

public record AlumnoResponse(
        Long id,
        Long usuarioId,
        String nombre,
        String email,
        String telefono,
        LocalDate fechaNacimiento,
        LocalDate fechaInscripcion,
        boolean activo
) {
}

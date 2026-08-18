package com.centroartesmusicales.backend.dto.alumno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AlumnoResponse(
        Long id,
        Long usuarioId,
        String nombre,
        String email,
        String telefono,
        LocalDate fechaNacimiento,
        LocalDate fechaInscripcion,
        boolean activo,
        LocalDate fechaPrimeraClase,
        List<LocalDate> fechasCicloClases,
        LocalDate proximoPago,
        BigDecimal precioMensual
) {
}

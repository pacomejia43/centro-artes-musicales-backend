package com.centroartesmusicales.backend.dto.alumno;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alta de alumno desde el panel de administración: el admin asigna un usuario y contraseña
 * (no necesariamente un correo real), a diferencia del alta pública (CrearAlumnoRequest, usada
 * por /api/auth/registro) donde sí se exige formato de correo.
 */
public record CrearAlumnoAdminRequest(
        @NotBlank String email,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank String nombre,
        String telefono,
        @Past LocalDate fechaNacimiento,
        LocalDate fechaPrimeraClase,
        /** Precio mensual particular; si se omite, se usa app.pagos.monto-mensual-default. */
        @Positive BigDecimal precioMensual
) {
}

package com.centroartesmusicales.backend.dto.alumno;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CrearAlumnoRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String nombre,
        String telefono,
        @Past LocalDate fechaNacimiento
) {
}

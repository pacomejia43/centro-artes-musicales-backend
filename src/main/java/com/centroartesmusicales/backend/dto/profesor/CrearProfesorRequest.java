package com.centroartesmusicales.backend.dto.profesor;

import com.centroartesmusicales.backend.model.Instrumento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record CrearProfesorRequest(
        @NotBlank String nombreCompleto,
        @Email String email,
        String telefono,
        @NotEmpty Set<Instrumento> especialidades
) {
}

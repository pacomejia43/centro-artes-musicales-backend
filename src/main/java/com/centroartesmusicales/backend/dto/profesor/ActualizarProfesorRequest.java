package com.centroartesmusicales.backend.dto.profesor;

import com.centroartesmusicales.backend.model.Instrumento;
import jakarta.validation.constraints.Email;

import java.util.Set;

public record ActualizarProfesorRequest(
        String nombreCompleto,
        @Email String email,
        String telefono,
        Set<Instrumento> especialidades,
        Boolean activo
) {
}

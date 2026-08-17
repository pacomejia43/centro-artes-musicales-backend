package com.centroartesmusicales.backend.dto.profesor;

import com.centroartesmusicales.backend.model.Instrumento;

import java.util.Set;

public record ProfesorResponse(
        Long id,
        String nombreCompleto,
        String email,
        String telefono,
        Set<Instrumento> especialidades,
        boolean activo
) {
}

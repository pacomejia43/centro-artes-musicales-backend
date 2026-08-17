package com.centroartesmusicales.backend.mapper;

import com.centroartesmusicales.backend.dto.profesor.ProfesorResponse;
import com.centroartesmusicales.backend.model.Profesor;

public final class ProfesorMapper {

    private ProfesorMapper() {
    }

    public static ProfesorResponse toResponse(Profesor profesor) {
        return new ProfesorResponse(
                profesor.getId(),
                profesor.getNombreCompleto(),
                profesor.getEmail(),
                profesor.getTelefono(),
                profesor.getEspecialidades(),
                profesor.isActivo()
        );
    }
}

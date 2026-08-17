package com.centroartesmusicales.backend.mapper;

import com.centroartesmusicales.backend.dto.alumno.AlumnoResponse;
import com.centroartesmusicales.backend.model.Alumno;

public final class AlumnoMapper {

    private AlumnoMapper() {
    }

    public static AlumnoResponse toResponse(Alumno alumno) {
        return new AlumnoResponse(
                alumno.getId(),
                alumno.getUsuario().getId(),
                alumno.getUsuario().getNombre(),
                alumno.getUsuario().getEmail(),
                alumno.getTelefono(),
                alumno.getFechaNacimiento(),
                alumno.getFechaInscripcion(),
                alumno.isActivo()
        );
    }
}

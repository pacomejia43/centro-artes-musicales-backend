package com.centroartesmusicales.backend.mapper;

import com.centroartesmusicales.backend.dto.alumno.AlumnoResponse;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.util.CicloClases;

import java.time.LocalDate;

public final class AlumnoMapper {

    private AlumnoMapper() {
    }

    public static AlumnoResponse toResponse(Alumno alumno) {
        LocalDate fechaPrimeraClase = alumno.getFechaPrimeraClase();
        return new AlumnoResponse(
                alumno.getId(),
                alumno.getUsuario().getId(),
                alumno.getUsuario().getNombre(),
                alumno.getUsuario().getEmail(),
                alumno.getTelefono(),
                alumno.getFechaNacimiento(),
                alumno.getFechaInscripcion(),
                alumno.isActivo(),
                fechaPrimeraClase,
                fechaPrimeraClase != null ? CicloClases.fechasClases(fechaPrimeraClase) : null,
                fechaPrimeraClase != null ? CicloClases.proximoPago(fechaPrimeraClase) : null
        );
    }
}

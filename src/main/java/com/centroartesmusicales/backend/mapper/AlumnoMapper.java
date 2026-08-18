package com.centroartesmusicales.backend.mapper;

import com.centroartesmusicales.backend.dto.alumno.AlumnoResponse;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.util.CicloClases;

import java.time.LocalDate;
import java.util.List;

public final class AlumnoMapper {

    private AlumnoMapper() {
    }

    /**
     * fechasCicloClases se recibe ya resuelto (ClaseService#resolverFechasCiclo) en vez de
     * calcularse aquí en puro, porque puede reflejar clases reales ya reagendadas — este mapper
     * no tiene acceso a ClaseRepository.
     */
    public static AlumnoResponse toResponse(Alumno alumno, List<LocalDate> fechasCicloClases) {
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
                fechasCicloClases,
                fechaPrimeraClase != null ? CicloClases.proximoPago(fechaPrimeraClase) : null
        );
    }
}

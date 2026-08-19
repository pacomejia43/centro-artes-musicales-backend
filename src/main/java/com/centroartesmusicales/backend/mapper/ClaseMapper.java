package com.centroartesmusicales.backend.mapper;

import com.centroartesmusicales.backend.dto.clase.ClaseResponse;
import com.centroartesmusicales.backend.dto.clase.SolicitudReagendacionResponse;
import com.centroartesmusicales.backend.model.Clase;
import com.centroartesmusicales.backend.model.SolicitudReagendacion;

public final class ClaseMapper {

    private ClaseMapper() {
    }

    public static ClaseResponse toResponse(Clase clase) {
        return new ClaseResponse(
                clase.getId(),
                clase.getAlumno().getId(),
                clase.getAlumno().getUsuario().getNombre(),
                clase.getProfesor() != null ? clase.getProfesor().getId() : null,
                clase.getProfesor() != null ? clase.getProfesor().getNombreCompleto() : null,
                clase.getInstrumento(),
                clase.getFechaHora(),
                clase.getDuracionMinutos(),
                clase.getEstado(),
                clase.getClaseOriginal() != null ? clase.getClaseOriginal().getId() : null,
                clase.getNotas()
        );
    }

    public static SolicitudReagendacionResponse toResponse(SolicitudReagendacion solicitud) {
        return new SolicitudReagendacionResponse(
                solicitud.getId(),
                solicitud.getClase().getId(),
                solicitud.getClase().getAlumno().getId(),
                solicitud.getClase().getAlumno().getUsuario().getNombre(),
                solicitud.getClase().getFechaHora(),
                solicitud.getFechaHoraPropuesta(),
                solicitud.getProfesorPropuesto() != null ? solicitud.getProfesorPropuesto().getId() : null,
                solicitud.getMotivo(),
                solicitud.getEstado(),
                solicitud.getMotivoRechazo(),
                solicitud.getClaseNueva() != null ? solicitud.getClaseNueva().getId() : null,
                solicitud.getRevisadoAt()
        );
    }
}

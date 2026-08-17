package com.centroartesmusicales.backend.dto.clase;

import com.centroartesmusicales.backend.model.EstadoSolicitud;

import java.time.LocalDateTime;

public record SolicitudReagendacionResponse(
        Long id,
        Long claseId,
        Long alumnoId,
        String alumnoNombre,
        LocalDateTime fechaHoraOriginal,
        LocalDateTime fechaHoraPropuesta,
        Long profesorPropuestoId,
        String motivo,
        EstadoSolicitud estado,
        String motivoRechazo,
        Long claseNuevaId,
        LocalDateTime revisadoAt
) {
}

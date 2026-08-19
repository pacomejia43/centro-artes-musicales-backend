package com.centroartesmusicales.backend.mapper;

import com.centroartesmusicales.backend.dto.suscripcion.SuscripcionResponse;
import com.centroartesmusicales.backend.model.Suscripcion;

public final class SuscripcionMapper {

    private SuscripcionMapper() {
    }

    public static SuscripcionResponse toResponse(Suscripcion suscripcion) {
        return new SuscripcionResponse(
                suscripcion.getId(),
                suscripcion.getEstado(),
                suscripcion.getMonto(),
                suscripcion.isCancelacionProgramada(),
                suscripcion.getFechaCancelacion()
        );
    }
}

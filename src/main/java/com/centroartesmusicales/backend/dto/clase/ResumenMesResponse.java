package com.centroartesmusicales.backend.dto.clase;

import com.centroartesmusicales.backend.model.Instrumento;

import java.util.List;

public record ResumenMesResponse(
        String periodo,
        int clasesTomadas,
        int limiteMensual,
        int clasesDisponibles,
        /** Desglose por instrumento; vacía si el alumno no tiene cupos particulares configurados
         *  (ver AlumnoInstrumentoCupo) y se rige por el límite mensual global de arriba. */
        List<ResumenInstrumentoItem> porInstrumento
) {

    public record ResumenInstrumentoItem(
            Instrumento instrumento,
            int tomadas,
            int cupoMensual,
            int disponibles
    ) {
    }
}

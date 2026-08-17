package com.centroartesmusicales.backend.dto.clase;

public record ResumenMesResponse(
        String periodo,
        int clasesTomadas,
        int limiteMensual,
        int clasesDisponibles
) {
}

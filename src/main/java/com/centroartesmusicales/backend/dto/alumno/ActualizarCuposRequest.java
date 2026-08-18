package com.centroartesmusicales.backend.dto.alumno;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Reemplaza de un solo golpe todos los cupos por instrumento del alumno. Una lista vacía borra
 * los cupos particulares y hace que el alumno vuelva a regirse por el límite mensual global.
 */
public record ActualizarCuposRequest(
        @NotNull @Valid List<CupoInstrumentoRequest> cupos
) {
}

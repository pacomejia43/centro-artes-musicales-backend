package com.centroartesmusicales.backend.dto.clase;

import com.centroartesmusicales.backend.model.EstadoClase;
import jakarta.validation.constraints.NotNull;

public record MarcarAsistenciaRequest(
        @NotNull EstadoClase estado
) {
}

package com.centroartesmusicales.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordRequest(
        @NotBlank String passwordActual,
        @NotBlank @Size(min = 8, max = 100) String passwordNueva
) {
}

package com.centroartesmusicales.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** El identificador puede ser un correo real (alta pública) o un usuario asignado por el admin. */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}

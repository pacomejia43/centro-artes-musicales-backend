package com.centroartesmusicales.backend.dto.auth;

import com.centroartesmusicales.backend.model.Role;

public record MeResponse(
        Long usuarioId,
        String nombre,
        String email,
        Role role
) {
}

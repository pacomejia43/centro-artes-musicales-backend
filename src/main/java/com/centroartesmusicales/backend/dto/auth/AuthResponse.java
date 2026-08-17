package com.centroartesmusicales.backend.dto.auth;

import com.centroartesmusicales.backend.model.Role;

public record AuthResponse(
        String token,
        Long usuarioId,
        String nombre,
        String email,
        Role role
) {
}

package com.centroartesmusicales.backend.controller;

import com.centroartesmusicales.backend.dto.alumno.CrearAlumnoRequest;
import com.centroartesmusicales.backend.dto.auth.AuthResponse;
import com.centroartesmusicales.backend.dto.auth.CambiarPasswordRequest;
import com.centroartesmusicales.backend.dto.auth.LoginRequest;
import com.centroartesmusicales.backend.dto.auth.MeResponse;
import com.centroartesmusicales.backend.security.SecurityUser;
import com.centroartesmusicales.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registro(@Valid @RequestBody CrearAlumnoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registro(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal SecurityUser securityUser) {
        return ResponseEntity.ok(authService.me(securityUser));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> cambiarPassword(@AuthenticationPrincipal SecurityUser securityUser,
                                                 @Valid @RequestBody CambiarPasswordRequest request) {
        authService.cambiarPassword(securityUser, request);
        return ResponseEntity.noContent().build();
    }
}

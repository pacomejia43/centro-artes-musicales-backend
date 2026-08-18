package com.centroartesmusicales.backend.service;

import com.centroartesmusicales.backend.dto.alumno.CrearAlumnoRequest;
import com.centroartesmusicales.backend.dto.auth.AuthResponse;
import com.centroartesmusicales.backend.dto.auth.CambiarPasswordRequest;
import com.centroartesmusicales.backend.dto.auth.LoginRequest;
import com.centroartesmusicales.backend.dto.auth.MeResponse;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
import com.centroartesmusicales.backend.security.JwtService;
import com.centroartesmusicales.backend.security.PasswordCipherService;
import com.centroartesmusicales.backend.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AlumnoService alumnoService;
    private final PasswordCipherService passwordCipherService;

    @Transactional
    public AuthResponse registro(CrearAlumnoRequest request) {
        Alumno alumno = alumnoService.crear(request.email(), request.password(), request.nombre(),
                request.telefono(), request.fechaNacimiento(), null, null);
        Usuario usuario = alumno.getUsuario();
        String token = jwtService.generateToken(new SecurityUser(usuario));
        return new AuthResponse(token, usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        String token = jwtService.generateToken(new SecurityUser(usuario));
        return new AuthResponse(token, usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRole());
    }

    public MeResponse me(SecurityUser securityUser) {
        return new MeResponse(securityUser.getId(), securityUser.getNombre(), securityUser.getUsername(), securityUser.getRole());
    }

    @Transactional
    public void cambiarPassword(SecurityUser securityUser, CambiarPasswordRequest request) {
        Usuario usuario = usuarioRepository.findById(securityUser.getId())
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPassword())) {
            throw new BadCredentialsException("La contraseña actual no es correcta");
        }

        usuario.setPassword(passwordEncoder.encode(request.passwordNueva()));
        usuario.setPasswordVisible(passwordCipherService.encriptar(request.passwordNueva()));
        usuarioRepository.save(usuario);
    }
}

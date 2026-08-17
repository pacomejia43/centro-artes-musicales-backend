package com.centroartesmusicales.backend.config;

import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Usuario;
import com.centroartesmusicales.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The public /api/auth/registro endpoint can only ever create ALUMNO users, so there is no
 * self-service path to the first admin account. This runner creates one from env-backed
 * properties on startup if no admin exists yet — and deliberately does nothing (just warns)
 * if those properties are blank, rather than shipping a default admin password.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        String email = appProperties.admin().bootstrap().email();
        String password = appProperties.admin().bootstrap().password();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No existe ningún administrador y ADMIN_BOOTSTRAP_EMAIL/ADMIN_BOOTSTRAP_PASSWORD "
                    + "no están configurados. No se creará un admin inicial. Configure esas variables "
                    + "de entorno y reinicie la aplicación.");
            return;
        }

        Usuario admin = Usuario.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .nombre(appProperties.admin().bootstrap().nombre())
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        usuarioRepository.save(admin);
        log.info("Administrador inicial creado con email {}", email);
    }
}

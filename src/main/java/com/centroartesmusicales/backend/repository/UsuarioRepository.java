package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.Role;
import com.centroartesmusicales.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);
}

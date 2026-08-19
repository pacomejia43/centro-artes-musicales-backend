package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.Alumno;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlumnoRepository extends JpaRepository<Alumno, Long> {

    Optional<Alumno> findByUsuarioId(Long usuarioId);

    Optional<Alumno> findByStripeCustomerId(String stripeCustomerId);

    Page<Alumno> findByActivo(boolean activo, Pageable pageable);

    Page<Alumno> findByUsuario_NombreContainingIgnoreCase(String nombre, Pageable pageable);
}

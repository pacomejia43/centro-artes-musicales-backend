package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    Optional<Suscripcion> findByAlumno_Id(Long alumnoId);

    Optional<Suscripcion> findByStripeSubscriptionId(String stripeSubscriptionId);
}

package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.StripeWebhookEvento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeWebhookEventoRepository extends JpaRepository<StripeWebhookEvento, Long> {

    boolean existsByStripeEventId(String stripeEventId);
}

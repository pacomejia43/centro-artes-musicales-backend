package com.centroartesmusicales.backend.repository;

import com.centroartesmusicales.backend.model.StripePriceCache;
import com.centroartesmusicales.backend.model.TipoPrecioStripe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Optional;

public interface StripePriceCacheRepository extends JpaRepository<StripePriceCache, Long> {

    Optional<StripePriceCache> findByMontoAndTipo(BigDecimal monto, TipoPrecioStripe tipo);
}

package com.centroartesmusicales.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Admin admin,
        String timezone,
        Clases clases,
        Pagos pagos
) {

    public record Jwt(String secret, long expirationMs) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Admin(Bootstrap bootstrap) {
        public record Bootstrap(String email, String password, String nombre) {
        }
    }

    public record Clases(int limiteMensual, int horasMinimasReagendar, int duracionDefaultMinutos) {
    }

    public record Pagos(BigDecimal montoMensualDefault) {
    }
}

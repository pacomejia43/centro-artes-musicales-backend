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
        Pagos pagos,
        Stripe stripe,
        String frontendUrl
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

    /**
     * Recorta espacios/saltos de línea de las tres claves en el constructor compacto — un
     * copy-paste hacia la variable de entorno en Railway (o donde sea) fácilmente arrastra un
     * salto de línea o un espacio de más, y el SDK de Stripe rechaza la clave completa con
     * "API key is invalid, as it contains whitespace" en cuanto eso pasa. Mejor blindarlo aquí,
     * en el único punto donde estos valores entran al sistema, que confiar en que cada lugar que
     * los usa se acuerde de limpiarlos.
     */
    public record Stripe(String secretKey, String publishableKey, String webhookSecret) {

        public Stripe {
            secretKey = recortar(secretKey);
            publishableKey = recortar(publishableKey);
            webhookSecret = recortar(webhookSecret);
        }

        private static String recortar(String valor) {
            return valor != null ? valor.trim() : valor;
        }

        public boolean configurado() {
            return secretKey != null && !secretKey.isBlank();
        }
    }
}

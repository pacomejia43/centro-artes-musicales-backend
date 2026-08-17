package com.centroartesmusicales.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final AppProperties appProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Copiamos los patrones configurados en appProperties para no perderlos
        List<String> origins = new ArrayList<>();
        if (appProperties.cors() != null && appProperties.cors().allowedOrigins() != null) {
            origins.addAll(appProperties.cors().allowedOrigins());
        }
        
        // Agregamos explícitamente localhost para que Claude pueda probar el panel localmente en el puerto 5500
        origins.add("http://localhost:5500");
        origins.add("http://127.0.0.1:5500");

        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
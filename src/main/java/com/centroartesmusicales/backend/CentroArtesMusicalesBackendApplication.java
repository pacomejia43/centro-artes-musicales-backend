package com.centroartesmusicales.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CentroArtesMusicalesBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CentroArtesMusicalesBackendApplication.class, args);
    }

}

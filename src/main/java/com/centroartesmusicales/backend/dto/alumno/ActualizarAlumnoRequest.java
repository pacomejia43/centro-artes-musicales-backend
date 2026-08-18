package com.centroartesmusicales.backend.dto.alumno;

import com.centroartesmusicales.backend.model.Instrumento;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record ActualizarAlumnoRequest(
        String nombre,
        /** Usuario para iniciar sesión — no se exige formato de correo: lo asigna el admin. */
        String email,
        String telefono,
        @Past LocalDate fechaNacimiento,
        String googleDocsUrl1,
        Instrumento instrumentoBitacora1,
        String googleDocsUrl2,
        Instrumento instrumentoBitacora2,
        Boolean activo,
        LocalDate fechaPrimeraClase
) {
}

package com.centroartesmusicales.backend.dto.alumno;

/** password es null si el alumno todavía no tiene ninguna contraseña capturada desde que existe
 *  esta función (ver AlumnoService#obtenerPasswordVisible). */
public record PasswordVisibleResponse(String password) {
}

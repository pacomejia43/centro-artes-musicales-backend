package com.centroartesmusicales.backend.util;

import java.time.LocalDate;
import java.util.List;

/**
 * Unidad de cobro de la escuela: 4 clases semanales contadas desde la fecha real de la primera
 * clase del alumno, no desde el inicio del mes calendario (la mayoría de los alumnos no arrancan
 * el día 1). El próximo pago se anuncia 4 semanas (28 días) después de esa primera clase.
 */
public final class CicloClases {

    public static final int CLASES_POR_CICLO = 4;
    public static final int DIAS_POR_CICLO = CLASES_POR_CICLO * 7;

    private CicloClases() {
    }

    public static List<LocalDate> fechasClases(LocalDate primeraClase) {
        return List.of(
                primeraClase,
                primeraClase.plusWeeks(1),
                primeraClase.plusWeeks(2),
                primeraClase.plusWeeks(3)
        );
    }

    public static LocalDate proximoPago(LocalDate primeraClase) {
        return primeraClase.plusDays(DIAS_POR_CICLO);
    }
}

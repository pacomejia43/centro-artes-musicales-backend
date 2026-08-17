package com.centroartesmusicales.backend.dto.clase;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Used by the admin's direct reschedule endpoint — no approval gate, admin bypasses the notice window. */
public record ReagendarRequest(
        @NotNull @Future LocalDateTime fechaHoraPropuesta,
        Long profesorId
) {
}

package com.centroartesmusicales.backend.dto.alumno;

import com.centroartesmusicales.backend.model.Instrumento;

public record BitacoraResponse(
        String googleDocsUrl1,
        Instrumento instrumentoBitacora1,
        String googleDocsUrl2,
        Instrumento instrumentoBitacora2
) {
}

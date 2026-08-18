package com.centroartesmusicales.backend.controller.alumno;

import com.centroartesmusicales.backend.dto.alumno.ActualizarPerfilRequest;
import com.centroartesmusicales.backend.dto.alumno.AlumnoResponse;
import com.centroartesmusicales.backend.dto.alumno.BitacoraResponse;
import com.centroartesmusicales.backend.mapper.AlumnoMapper;
import com.centroartesmusicales.backend.security.SecurityUser;
import com.centroartesmusicales.backend.service.AlumnoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Always scoped to the authenticated principal — never takes an {alumnoId} path param, so
 * "only the owner can see their bitácora" is structural rather than a check to remember.
 */
@RestController
@RequestMapping("/api/alumno")
@RequiredArgsConstructor
public class AlumnoSelfController {

    private final AlumnoService alumnoService;

    @GetMapping("/perfil")
    public ResponseEntity<AlumnoResponse> perfil(@AuthenticationPrincipal SecurityUser securityUser) {
        return ResponseEntity.ok(AlumnoMapper.toResponse(alumnoService.obtenerPorUsuarioId(securityUser.getId())));
    }

    @PutMapping("/perfil")
    public ResponseEntity<AlumnoResponse> actualizarPerfil(@AuthenticationPrincipal SecurityUser securityUser,
                                                             @Valid @RequestBody ActualizarPerfilRequest request) {
        var alumno = alumnoService.actualizarPerfil(securityUser.getId(), request);
        return ResponseEntity.ok(AlumnoMapper.toResponse(alumno));
    }

    @GetMapping("/bitacora")
    public ResponseEntity<BitacoraResponse> bitacora(@AuthenticationPrincipal SecurityUser securityUser) {
        var alumno = alumnoService.obtenerPorUsuarioId(securityUser.getId());
        return ResponseEntity.ok(new BitacoraResponse(alumno.getGoogleDocsUrl1(), alumno.getInstrumentoBitacora1(),
                alumno.getGoogleDocsUrl2(), alumno.getInstrumentoBitacora2()));
    }
}

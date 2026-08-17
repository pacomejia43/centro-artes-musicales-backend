package com.centroartesmusicales.backend.controller.alumno;

import com.centroartesmusicales.backend.dto.clase.ClaseResponse;
import com.centroartesmusicales.backend.dto.clase.ResumenMesResponse;
import com.centroartesmusicales.backend.dto.clase.SolicitarReagendacionRequest;
import com.centroartesmusicales.backend.dto.clase.SolicitudReagendacionResponse;
import com.centroartesmusicales.backend.mapper.ClaseMapper;
import com.centroartesmusicales.backend.model.EstadoClase;
import com.centroartesmusicales.backend.security.SecurityUser;
import com.centroartesmusicales.backend.service.ClaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** Always scoped to the authenticated alumno — never takes an {alumnoId} path param. */
@RestController
@RequestMapping("/api/alumno")
@RequiredArgsConstructor
public class ClaseSelfController {

    private final ClaseService claseService;

    @GetMapping("/clases")
    public ResponseEntity<Page<ClaseResponse>> misClases(@AuthenticationPrincipal SecurityUser securityUser,
                                                           @RequestParam(required = false) EstadoClase estado,
                                                           Pageable pageable) {
        var pagina = claseService.listarPropias(securityUser.getId(), estado, pageable);
        return ResponseEntity.ok(pagina.map(ClaseMapper::toResponse));
    }

    @GetMapping("/clases/{id}")
    public ResponseEntity<ClaseResponse> miClase(@AuthenticationPrincipal SecurityUser securityUser,
                                                  @PathVariable Long id) {
        var clase = claseService.obtenerPropia(securityUser.getId(), id);
        return ResponseEntity.ok(ClaseMapper.toResponse(clase));
    }

    @GetMapping("/resumen-mes")
    public ResponseEntity<ResumenMesResponse> resumenMes(@AuthenticationPrincipal SecurityUser securityUser,
                                                           @RequestParam(required = false) YearMonth periodo) {
        return ResponseEntity.ok(claseService.resumenMesPropio(securityUser.getId(), periodo));
    }

    @PostMapping("/clases/{id}/solicitudes-reagendacion")
    public ResponseEntity<SolicitudReagendacionResponse> solicitarReagendo(
            @AuthenticationPrincipal SecurityUser securityUser,
            @PathVariable Long id,
            @Valid @RequestBody SolicitarReagendacionRequest request) {
        var solicitud = claseService.crearSolicitud(securityUser.getId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClaseMapper.toResponse(solicitud));
    }

    @GetMapping("/solicitudes-reagendacion")
    public ResponseEntity<Page<SolicitudReagendacionResponse>> misSolicitudes(
            @AuthenticationPrincipal SecurityUser securityUser, Pageable pageable) {
        var pagina = claseService.listarSolicitudesPropias(securityUser.getId(), pageable);
        return ResponseEntity.ok(pagina.map(ClaseMapper::toResponse));
    }
}

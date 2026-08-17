package com.centroartesmusicales.backend.controller.admin;

import com.centroartesmusicales.backend.dto.clase.RechazarSolicitudRequest;
import com.centroartesmusicales.backend.dto.clase.SolicitudReagendacionResponse;
import com.centroartesmusicales.backend.mapper.ClaseMapper;
import com.centroartesmusicales.backend.model.EstadoSolicitud;
import com.centroartesmusicales.backend.security.SecurityUser;
import com.centroartesmusicales.backend.service.ClaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/solicitudes-reagendacion")
@RequiredArgsConstructor
public class SolicitudReagendacionAdminController {

    private final ClaseService claseService;

    @GetMapping
    public ResponseEntity<Page<SolicitudReagendacionResponse>> listar(
            @RequestParam(required = false) EstadoSolicitud estado, Pageable pageable) {
        var pagina = claseService.listarSolicitudes(estado, pageable);
        return ResponseEntity.ok(pagina.map(ClaseMapper::toResponse));
    }

    @PutMapping("/{id}/aprobar")
    public ResponseEntity<SolicitudReagendacionResponse> aprobar(@PathVariable Long id,
                                                                   @AuthenticationPrincipal SecurityUser admin) {
        var solicitud = claseService.aprobarSolicitud(id, admin.getId());
        return ResponseEntity.ok(ClaseMapper.toResponse(solicitud));
    }

    @PutMapping("/{id}/rechazar")
    public ResponseEntity<SolicitudReagendacionResponse> rechazar(@PathVariable Long id,
                                                                    @AuthenticationPrincipal SecurityUser admin,
                                                                    @RequestBody(required = false) RechazarSolicitudRequest request) {
        String motivo = request != null ? request.motivoRechazo() : null;
        var solicitud = claseService.rechazarSolicitud(id, admin.getId(), motivo);
        return ResponseEntity.ok(ClaseMapper.toResponse(solicitud));
    }
}

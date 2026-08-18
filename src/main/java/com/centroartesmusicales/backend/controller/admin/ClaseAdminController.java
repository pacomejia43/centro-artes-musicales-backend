package com.centroartesmusicales.backend.controller.admin;

import com.centroartesmusicales.backend.dto.clase.CancelarClaseRequest;
import com.centroartesmusicales.backend.dto.clase.ClaseResponse;
import com.centroartesmusicales.backend.dto.clase.MarcarAsistenciaRequest;
import com.centroartesmusicales.backend.dto.clase.ProgramarCicloClasesRequest;
import com.centroartesmusicales.backend.dto.clase.ProgramarClaseRequest;
import com.centroartesmusicales.backend.dto.clase.ReagendarRequest;
import com.centroartesmusicales.backend.model.EstadoClase;
import com.centroartesmusicales.backend.mapper.ClaseMapper;
import com.centroartesmusicales.backend.service.ClaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/clases")
@RequiredArgsConstructor
public class ClaseAdminController {

    private final ClaseService claseService;

    @PostMapping
    public ResponseEntity<ClaseResponse> programar(@Valid @RequestBody ProgramarClaseRequest request) {
        var clase = claseService.programar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClaseMapper.toResponse(clase));
    }

    @PostMapping("/ciclo")
    public ResponseEntity<List<ClaseResponse>> programarCiclo(@Valid @RequestBody ProgramarCicloClasesRequest request) {
        var clases = claseService.programarCiclo(request.alumnoId(), request.asignaciones(),
                request.duracionMinutos(), request.notas());
        return ResponseEntity.status(HttpStatus.CREATED).body(clases.stream().map(ClaseMapper::toResponse).toList());
    }

    @GetMapping
    public ResponseEntity<Page<ClaseResponse>> listar(
            @RequestParam(required = false) Long alumnoId,
            @RequestParam(required = false) Long profesorId,
            @RequestParam(required = false) EstadoClase estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            Pageable pageable) {
        var pagina = claseService.listar(alumnoId, profesorId, estado, desde, hasta, pageable);
        return ResponseEntity.ok(pagina.map(ClaseMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaseResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ClaseMapper.toResponse(claseService.obtenerPorId(id)));
    }

    @PutMapping("/{id}/asistencia")
    public ResponseEntity<ClaseResponse> marcarAsistencia(@PathVariable Long id,
                                                            @Valid @RequestBody MarcarAsistenciaRequest request) {
        var clase = claseService.marcarAsistencia(id, request.estado());
        return ResponseEntity.ok(ClaseMapper.toResponse(clase));
    }

    @PutMapping("/{id}/reagendar")
    public ResponseEntity<ClaseResponse> reagendar(@PathVariable Long id,
                                                     @Valid @RequestBody ReagendarRequest request) {
        var nueva = claseService.reagendarDirecto(id, request);
        return ResponseEntity.ok(ClaseMapper.toResponse(nueva));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable Long id, @RequestBody(required = false) CancelarClaseRequest request) {
        claseService.cancelar(id, request != null ? request.motivo() : null);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        claseService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

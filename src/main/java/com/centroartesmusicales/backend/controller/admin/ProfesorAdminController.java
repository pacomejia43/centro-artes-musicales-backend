package com.centroartesmusicales.backend.controller.admin;

import com.centroartesmusicales.backend.dto.profesor.ActualizarProfesorRequest;
import com.centroartesmusicales.backend.dto.profesor.CrearProfesorRequest;
import com.centroartesmusicales.backend.dto.profesor.ProfesorResponse;
import com.centroartesmusicales.backend.mapper.ProfesorMapper;
import com.centroartesmusicales.backend.service.ProfesorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

@RestController
@RequestMapping("/api/admin/profesores")
@RequiredArgsConstructor
public class ProfesorAdminController {

    private final ProfesorService profesorService;

    @PostMapping
    public ResponseEntity<ProfesorResponse> crear(@Valid @RequestBody CrearProfesorRequest request) {
        var profesor = profesorService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfesorMapper.toResponse(profesor));
    }

    @GetMapping
    public ResponseEntity<Page<ProfesorResponse>> listar(@RequestParam(required = false) Boolean activo,
                                                           Pageable pageable) {
        return ResponseEntity.ok(profesorService.listar(activo, pageable).map(ProfesorMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfesorResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ProfesorMapper.toResponse(profesorService.obtenerPorId(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfesorResponse> actualizar(@PathVariable Long id,
                                                         @Valid @RequestBody ActualizarProfesorRequest request) {
        return ResponseEntity.ok(ProfesorMapper.toResponse(profesorService.actualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        profesorService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}

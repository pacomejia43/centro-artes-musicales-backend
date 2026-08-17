package com.centroartesmusicales.backend.controller.admin;

import com.centroartesmusicales.backend.dto.alumno.ActualizarAlumnoRequest;
import com.centroartesmusicales.backend.dto.alumno.AlumnoResponse;
import com.centroartesmusicales.backend.dto.alumno.BitacoraResponse;
import com.centroartesmusicales.backend.dto.alumno.CrearAlumnoRequest;
import com.centroartesmusicales.backend.dto.alumno.ResetPasswordRequest;
import com.centroartesmusicales.backend.dto.clase.ResumenMesResponse;
import com.centroartesmusicales.backend.mapper.AlumnoMapper;
import com.centroartesmusicales.backend.model.Alumno;
import com.centroartesmusicales.backend.service.AlumnoService;
import com.centroartesmusicales.backend.service.ClaseService;
import com.centroartesmusicales.backend.service.PagoService;
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

import java.time.YearMonth;

@RestController
@RequestMapping("/api/admin/alumnos")
@RequiredArgsConstructor
public class AlumnoAdminController {

    private final AlumnoService alumnoService;
    private final ClaseService claseService;
    private final PagoService pagoService;

    @PostMapping
    public ResponseEntity<AlumnoResponse> crear(@Valid @RequestBody CrearAlumnoRequest request) {
        var alumno = alumnoService.crear(request);
        asegurarCargoDeCiclo(alumno);
        return ResponseEntity.status(HttpStatus.CREATED).body(AlumnoMapper.toResponse(alumno));
    }

    @GetMapping
    public ResponseEntity<Page<AlumnoResponse>> listar(@RequestParam(required = false) Boolean activo,
                                                       @RequestParam(required = false) String nombre,
                                                       Pageable pageable) {
        return ResponseEntity.ok(alumnoService.listar(activo, nombre, pageable).map(AlumnoMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlumnoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(AlumnoMapper.toResponse(alumnoService.obtenerPorId(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlumnoResponse> actualizar(@PathVariable Long id,
                                                       @Valid @RequestBody ActualizarAlumnoRequest request) {
        var alumno = alumnoService.actualizar(id, request);
        asegurarCargoDeCiclo(alumno);
        return ResponseEntity.ok(AlumnoMapper.toResponse(alumno));
    }

    /** Si el alumno ya tiene fecha de primera clase, garantiza que exista el cargo de ese ciclo (ver PagoService). */
    private void asegurarCargoDeCiclo(Alumno alumno) {
        if (alumno.getFechaPrimeraClase() != null) {
            pagoService.crearCargoCicloSiNoExiste(alumno.getId(), alumno.getFechaPrimeraClase());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        alumnoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        alumnoService.resetPassword(id, request.passwordNueva());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/bitacora")
    public ResponseEntity<BitacoraResponse> bitacora(@PathVariable Long id) {
        return ResponseEntity.ok(new BitacoraResponse(alumnoService.obtenerBitacora(id)));
    }

    @GetMapping("/{id}/resumen-mes")
    public ResponseEntity<ResumenMesResponse> resumenMes(@PathVariable Long id,
                                                          @RequestParam(required = false) YearMonth periodo) {
        return ResponseEntity.ok(claseService.resumenMes(id, periodo));
    }
}
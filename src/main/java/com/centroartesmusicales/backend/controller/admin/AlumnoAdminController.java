package com.centroartesmusicales.backend.controller.admin;

import com.centroartesmusicales.backend.dto.alumno.ActualizarAlumnoRequest;
import com.centroartesmusicales.backend.dto.alumno.ActualizarCuposRequest;
import com.centroartesmusicales.backend.dto.alumno.AlumnoResponse;
import com.centroartesmusicales.backend.dto.alumno.BitacoraResponse;
import com.centroartesmusicales.backend.dto.alumno.CrearAlumnoAdminRequest;
import com.centroartesmusicales.backend.dto.alumno.CupoInstrumentoResponse;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/alumnos")
@RequiredArgsConstructor
public class AlumnoAdminController {

    private final AlumnoService alumnoService;
    private final ClaseService claseService;
    private final PagoService pagoService;

    @PostMapping
    public ResponseEntity<AlumnoResponse> crear(@Valid @RequestBody CrearAlumnoAdminRequest request) {
        var alumno = alumnoService.crear(request.email(), request.password(), request.nombre(),
                request.telefono(), request.fechaNacimiento(), request.fechaPrimeraClase(), request.precioMensual());
        asegurarCargoDeCiclo(alumno);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(alumno));
    }

    @GetMapping
    public ResponseEntity<Page<AlumnoResponse>> listar(@RequestParam(required = false) Boolean activo,
                                                       @RequestParam(required = false) String nombre,
                                                       Pageable pageable) {
        return ResponseEntity.ok(alumnoService.listar(activo, nombre, pageable).map(this::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlumnoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(alumnoService.obtenerPorId(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlumnoResponse> actualizar(@PathVariable Long id,
                                                       @Valid @RequestBody ActualizarAlumnoRequest request) {
        var alumno = alumnoService.actualizar(id, request);
        asegurarCargoDeCiclo(alumno);
        return ResponseEntity.ok(toResponse(alumno));
    }

    /** Si el alumno ya tiene fecha de primera clase, garantiza que exista el cargo de ese ciclo (ver PagoService). */
    private void asegurarCargoDeCiclo(Alumno alumno) {
        if (alumno.getFechaPrimeraClase() != null) {
            pagoService.crearCargoCicloSiNoExiste(alumno.getId(), alumno.getFechaPrimeraClase());
        }
    }

    /** Resuelve las fechas reales del ciclo (si ya se agendaron) antes de mapear a la respuesta. */
    private AlumnoResponse toResponse(Alumno alumno) {
        List<LocalDate> fechasCiclo = alumno.getFechaPrimeraClase() != null
                ? claseService.resolverFechasCiclo(alumno.getId(), alumno.getFechaPrimeraClase())
                : null;
        return AlumnoMapper.toResponse(alumno, fechasCiclo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        alumnoService.eliminar(id);
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
        Alumno alumno = alumnoService.obtenerPorId(id);
        return ResponseEntity.ok(new BitacoraResponse(alumno.getGoogleDocsUrl1(), alumno.getInstrumentoBitacora1(),
                alumno.getGoogleDocsUrl2(), alumno.getInstrumentoBitacora2()));
    }

    @GetMapping("/{id}/resumen-mes")
    public ResponseEntity<ResumenMesResponse> resumenMes(@PathVariable Long id) {
        return ResponseEntity.ok(claseService.resumenMes(id));
    }

    @GetMapping("/{id}/cupos")
    public ResponseEntity<List<CupoInstrumentoResponse>> obtenerCupos(@PathVariable Long id) {
        var cupos = alumnoService.obtenerCupos(id).stream()
                .map(c -> new CupoInstrumentoResponse(c.getInstrumento(), c.getCupoMensual()))
                .toList();
        return ResponseEntity.ok(cupos);
    }

    @PutMapping("/{id}/cupos")
    public ResponseEntity<List<CupoInstrumentoResponse>> actualizarCupos(@PathVariable Long id,
                                                                          @Valid @RequestBody ActualizarCuposRequest request) {
        var cupos = alumnoService.actualizarCupos(id, request.cupos()).stream()
                .map(c -> new CupoInstrumentoResponse(c.getInstrumento(), c.getCupoMensual()))
                .toList();
        return ResponseEntity.ok(cupos);
    }
}
package com.centroartesmusicales.backend.controller.admin;

import com.centroartesmusicales.backend.dto.pago.CrearPagoRequest;
import com.centroartesmusicales.backend.dto.pago.PagoResponse;
import com.centroartesmusicales.backend.dto.pago.PagoTransaccionResponse;
import com.centroartesmusicales.backend.dto.pago.RechazarTransaccionRequest;
import com.centroartesmusicales.backend.dto.pago.RegistrarTransaccionRequest;
import com.centroartesmusicales.backend.mapper.PagoMapper;
import com.centroartesmusicales.backend.model.EstadoPago;
import com.centroartesmusicales.backend.model.Pago;
import com.centroartesmusicales.backend.security.SecurityUser;
import com.centroartesmusicales.backend.service.PagoService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class PagoAdminController {

    private final PagoService pagoService;

    private PagoResponse toResponse(Pago pago) {
        return PagoMapper.toResponse(pago, pagoService.montoPagado(pago), pagoService.esVencido(pago));
    }

    @PostMapping("/alumnos/{alumnoId}/pagos")
    public ResponseEntity<PagoResponse> crearCargo(@PathVariable Long alumnoId,
                                                     @Valid @RequestBody CrearPagoRequest request) {
        Pago pago = pagoService.crearCargo(alumnoId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(pago));
    }

    @GetMapping("/alumnos/{alumnoId}/pagos")
    public ResponseEntity<Page<PagoResponse>> historialAlumno(@PathVariable Long alumnoId, Pageable pageable) {
        Page<PagoResponse> pagina = pagoService.listar(alumnoId, null, null, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/pagos")
    public ResponseEntity<Page<PagoResponse>> listar(@RequestParam(required = false) Long alumnoId,
                                                       @RequestParam(required = false) EstadoPago estado,
                                                       @RequestParam(required = false) YearMonth periodo,
                                                       Pageable pageable) {
        Page<PagoResponse> pagina = pagoService.listar(alumnoId, estado, periodo, pageable).map(this::toResponse);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/pagos/{id}")
    public ResponseEntity<PagoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(pagoService.obtenerPorId(id)));
    }

    @PostMapping("/pagos/{id}/transacciones")
    public ResponseEntity<PagoTransaccionResponse> registrarTransaccion(@PathVariable Long id,
                                                                          @AuthenticationPrincipal SecurityUser admin,
                                                                          @Valid @RequestBody RegistrarTransaccionRequest request) {
        var transaccion = pagoService.registrarTransaccionAdmin(id, admin.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PagoMapper.toResponse(transaccion));
    }

    @PutMapping("/pagos/transacciones/{id}/confirmar")
    public ResponseEntity<PagoTransaccionResponse> confirmarTransaccion(@PathVariable Long id,
                                                                          @AuthenticationPrincipal SecurityUser admin) {
        var transaccion = pagoService.confirmarTransaccion(id, admin.getId());
        return ResponseEntity.ok(PagoMapper.toResponse(transaccion));
    }

    @PutMapping("/pagos/transacciones/{id}/rechazar")
    public ResponseEntity<PagoTransaccionResponse> rechazarTransaccion(@PathVariable Long id,
                                                                         @AuthenticationPrincipal SecurityUser admin,
                                                                         @RequestBody(required = false) RechazarTransaccionRequest request) {
        String motivo = request != null ? request.motivoRechazo() : null;
        var transaccion = pagoService.rechazarTransaccion(id, admin.getId(), motivo);
        return ResponseEntity.ok(PagoMapper.toResponse(transaccion));
    }
}

package com.centroartesmusicales.backend.controller.alumno;

import com.centroartesmusicales.backend.dto.pago.CheckoutSessionResponse;
import com.centroartesmusicales.backend.dto.suscripcion.SuscripcionResponse;
import com.centroartesmusicales.backend.exception.ResourceNotFoundException;
import com.centroartesmusicales.backend.mapper.SuscripcionMapper;
import com.centroartesmusicales.backend.security.SecurityUser;
import com.centroartesmusicales.backend.service.SuscripcionService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pago automático (Stripe Billing) de la mensualidad — siempre auto-referido al alumno
 *  autenticado, igual que PagoSelfController. */
@RestController
@RequestMapping("/api/alumno/suscripcion")
@RequiredArgsConstructor
public class SuscripcionSelfController {

    private final SuscripcionService suscripcionService;

    @GetMapping
    public ResponseEntity<SuscripcionResponse> obtener(@AuthenticationPrincipal SecurityUser securityUser) {
        return suscripcionService.obtenerPropia(securityUser.getId())
                .map(SuscripcionMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes un pago automático configurado."));
    }

    @PostMapping
    public ResponseEntity<CheckoutSessionResponse> activar(@AuthenticationPrincipal SecurityUser securityUser) throws StripeException {
        Session session = suscripcionService.iniciarActivacion(securityUser.getId());
        return ResponseEntity.ok(new CheckoutSessionResponse(session.getUrl()));
    }

    @DeleteMapping
    public ResponseEntity<Void> cancelar(@AuthenticationPrincipal SecurityUser securityUser) throws StripeException {
        suscripcionService.cancelar(securityUser.getId());
        return ResponseEntity.noContent().build();
    }
}

package com.centroartesmusicales.backend.controller.alumno;

import com.centroartesmusicales.backend.dto.pago.CheckoutSessionResponse;
import com.centroartesmusicales.backend.dto.pago.PagoResponse;
import com.centroartesmusicales.backend.dto.pago.PagoTransaccionResponse;
import com.centroartesmusicales.backend.dto.pago.RegistrarTransaccionRequest;
import com.centroartesmusicales.backend.dto.pago.StripeConfigResponse;
import com.centroartesmusicales.backend.mapper.PagoMapper;
import com.centroartesmusicales.backend.model.Pago;
import com.centroartesmusicales.backend.security.SecurityUser;
import com.centroartesmusicales.backend.service.PagoService;
import com.centroartesmusicales.backend.service.StripeCheckoutService;
import com.centroartesmusicales.backend.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
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
import org.springframework.web.bind.annotation.RestController;

/** Always scoped to the authenticated alumno — never takes an {alumnoId} path param. */
@RestController
@RequestMapping("/api/alumno/pagos")
@RequiredArgsConstructor
public class PagoSelfController {

    private final PagoService pagoService;
    private final StripeCheckoutService stripeCheckoutService;
    private final StripeService stripeService;

    private PagoResponse toResponse(Pago pago) {
        return PagoMapper.toResponse(pago, pagoService.montoPagado(pago), pagoService.esVencido(pago));
    }

    /** La publishable key de Stripe no es secreta, pero vive en el backend (no hardcodeada en el
     *  sitio estático) para poder rotarla vía variable de entorno sin tocar el frontend. */
    @GetMapping("/config")
    public ResponseEntity<StripeConfigResponse> configuracionStripe() {
        return ResponseEntity.ok(new StripeConfigResponse(stripeService.publishableKey()));
    }

    /** El monto de la Checkout Session lo calcula el backend a partir del saldo pendiente real
     *  del cargo — este endpoint no recibe ni acepta ningún importe del cliente. */
    @PostMapping("/{id}/checkout")
    public ResponseEntity<CheckoutSessionResponse> checkout(@AuthenticationPrincipal SecurityUser securityUser,
                                                              @PathVariable Long id) throws StripeException {
        Session session = stripeCheckoutService.crearCheckoutParaCargo(securityUser.getId(), id);
        return ResponseEntity.ok(new CheckoutSessionResponse(session.getUrl()));
    }

    @GetMapping
    public ResponseEntity<Page<PagoResponse>> misPagos(@AuthenticationPrincipal SecurityUser securityUser,
                                                         Pageable pageable) {
        Page<PagoResponse> pagina = pagoService.listarPropios(securityUser.getId(), pageable).map(this::toResponse);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/actual")
    public ResponseEntity<PagoResponse> pagoActual(@AuthenticationPrincipal SecurityUser securityUser) {
        return ResponseEntity.ok(toResponse(pagoService.obtenerActualPropio(securityUser.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagoResponse> obtener(@AuthenticationPrincipal SecurityUser securityUser,
                                                 @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(pagoService.obtenerPropio(securityUser.getId(), id)));
    }

    @PostMapping("/{id}/registrar")
    public ResponseEntity<PagoTransaccionResponse> registrarPago(@AuthenticationPrincipal SecurityUser securityUser,
                                                                   @PathVariable Long id,
                                                                   @Valid @RequestBody RegistrarTransaccionRequest request) {
        var transaccion = pagoService.autorreportarPago(securityUser.getId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PagoMapper.toResponse(transaccion));
    }
}

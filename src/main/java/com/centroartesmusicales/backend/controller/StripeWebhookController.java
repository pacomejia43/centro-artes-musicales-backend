package com.centroartesmusicales.backend.controller;

import com.centroartesmusicales.backend.service.StripeService;
import com.centroartesmusicales.backend.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único endpoint que Stripe llama directamente, sin JWT (ver SecurityConfig, permitAll solo para
 * esta ruta exacta). La autenticidad de cada solicitud la garantiza la firma Stripe-Signature, no
 * Spring Security — nunca se procesa un payload sin haber verificado esa firma primero.
 * <p>
 * El body se recibe como String crudo a propósito: Webhook.constructEvent necesita los mismos
 * bytes exactos que Stripe firmó, así que no debe pasar por (des)serialización JSON antes.
 * <p>
 * Si el procesamiento falla por cualquier motivo que no sea firma inválida, se deja propagar la
 * excepción (responde 500 vía GlobalExceptionHandler) para que Stripe reintente la entrega más
 * tarde — StripeWebhookService solo marca un evento como procesado si terminó sin errores.
 */
@Slf4j
@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeService stripeService;
    private final StripeWebhookService stripeWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                         @RequestHeader("Stripe-Signature") String firmaStripe) {
        Event event;
        try {
            event = stripeService.verificarYConstruirEvento(payload, firmaStripe);
        } catch (SignatureVerificationException ex) {
            log.warn("Webhook de Stripe rechazado: firma inválida ({}).", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        stripeWebhookService.procesar(event);
        return ResponseEntity.ok().build();
    }
}

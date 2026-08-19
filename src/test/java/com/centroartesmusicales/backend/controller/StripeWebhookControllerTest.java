package com.centroartesmusicales.backend.controller;

import com.centroartesmusicales.backend.service.StripeService;
import com.centroartesmusicales.backend.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Instanciado directamente (sin contexto de Spring/MockMvc), igual que el resto de las pruebas
 *  de este proyecto — aquí solo importa que una firma inválida nunca llegue a StripeWebhookService. */
@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripeService stripeService;
    @Mock
    private StripeWebhookService stripeWebhookService;

    @Test
    void webhook_firmaInvalida_rechazaConBadRequestYNoProcesaNada() throws Exception {
        StripeWebhookController controller = new StripeWebhookController(stripeService, stripeWebhookService);
        when(stripeService.verificarYConstruirEvento(any(), any()))
                .thenThrow(new SignatureVerificationException("firma no coincide", "sig_falsa"));

        ResponseEntity<Void> respuesta = controller.webhook("{}", "t=1,v1=firma_falsa");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stripeWebhookService, never()).procesar(any());
    }

    @Test
    void webhook_firmaValida_procesaElEventoYRespondeOk() throws Exception {
        StripeWebhookController controller = new StripeWebhookController(stripeService, stripeWebhookService);
        Event eventoFalso = new Event();
        when(stripeService.verificarYConstruirEvento(any(), any())).thenReturn(eventoFalso);

        ResponseEntity<Void> respuesta = controller.webhook("{}", "t=1,v1=firma_valida");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(stripeWebhookService).procesar(eventoFalso);
    }
}

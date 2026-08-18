package com.centroartesmusicales.backend.security;

import com.centroartesmusicales.backend.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifrado reversible (AES-256-GCM) para poder mostrarle al administrador la contraseña vigente
 * de un alumno (ver AlumnoService#obtenerPasswordVisible) — separado del hash de un solo sentido
 * en Usuario.password, que sigue siendo lo único que se usa para autenticar. La clave sale de
 * app.security.password-visible-key (env var PASSWORD_VISIBLE_KEY), nunca del código.
 */
@Component
public class PasswordCipherService {

    private static final int TAMANO_IV_BYTES = 12;
    private static final int TAMANO_TAG_BITS = 128;

    private final SecretKeySpec clave;

    public PasswordCipherService(AppProperties appProperties) {
        String secreto = appProperties.security().passwordVisibleKey();
        byte[] keyBytes = Base64.getDecoder().decode(secreto);
        this.clave = new SecretKeySpec(keyBytes, "AES");
    }

    public String encriptar(String textoPlano) {
        try {
            byte[] iv = new byte[TAMANO_IV_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));
            ByteBuffer combinado = ByteBuffer.allocate(iv.length + cifrado.length);
            combinado.put(iv).put(cifrado);
            return Base64.getEncoder().encodeToString(combinado.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al encriptar la contraseña", e);
        }
    }

    public String desencriptar(String valorCifrado) {
        try {
            byte[] combinado = Base64.getDecoder().decode(valorCifrado);
            byte[] iv = Arrays.copyOfRange(combinado, 0, TAMANO_IV_BYTES);
            byte[] cifrado = Arrays.copyOfRange(combinado, TAMANO_IV_BYTES, combinado.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al desencriptar la contraseña", e);
        }
    }
}

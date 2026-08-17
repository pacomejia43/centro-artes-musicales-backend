package com.centroartesmusicales.backend.exception;

/**
 * Base type for 409-Conflict business-rule violations (cupo mensual, conflicto de horario,
 * plazo de reagendo, etc.). Subclassed in the clase/pago packages for specific rules.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}

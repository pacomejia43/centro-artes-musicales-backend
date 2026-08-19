package com.centroartesmusicales.backend.model;

/**
 * Un mismo monto necesita un Stripe Price distinto según sea cobro único o recurrente (son
 * objetos diferentes en Stripe) — StripePriceCache los cachea por separado para no crear Prices
 * duplicados en ninguno de los dos casos.
 */
public enum TipoPrecioStripe {
    UNICO,
    RECURRENTE
}

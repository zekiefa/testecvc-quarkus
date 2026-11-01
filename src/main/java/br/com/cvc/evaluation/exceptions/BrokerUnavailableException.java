package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;

/**
 * O broker de catálogo/tarifas não respondeu. A reserva não pode ser criada sem tarifa
 * atual, e inventar preço é pior do que falhar (ADR-002): o cliente recebe 503.
 */
public class BrokerUnavailableException extends CustomException {
    public BrokerUnavailableException(final String message) {
        super(message, Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
    }
}

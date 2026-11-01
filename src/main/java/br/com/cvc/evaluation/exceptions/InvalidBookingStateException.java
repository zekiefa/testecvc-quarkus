package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;

/**
 * Transição de estado não permitida (ex.: check-out de uma reserva cancelada).
 * É conflito com o estado atual do recurso, portanto 409.
 */
public class InvalidBookingStateException extends CustomException {
    public InvalidBookingStateException(final String message) {
        super(message, Response.Status.CONFLICT.getStatusCode());
    }
}

package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;

/** Não há mais capacidade para o quarto em alguma noite do período. Conflito, não erro. */
public class RoomUnavailableException extends CustomException {
    public RoomUnavailableException(final String message) {
        super(message, Response.Status.CONFLICT.getStatusCode());
    }
}

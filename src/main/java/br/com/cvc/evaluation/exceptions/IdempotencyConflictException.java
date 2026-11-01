package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;

/**
 * A mesma Idempotency-Key já foi usada por outro usuário. Não devolvemos a reserva
 * existente — isso vazaria dados de terceiros — nem criamos outra.
 */
public class IdempotencyConflictException extends CustomException {
    public IdempotencyConflictException(final String message) {
        super(message, Response.Status.CONFLICT.getStatusCode());
    }
}

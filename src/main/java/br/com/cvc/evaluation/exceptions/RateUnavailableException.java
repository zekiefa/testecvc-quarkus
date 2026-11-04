package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;

/**
 * O broker não tem tarifa publicada para alguma noite do período.
 *
 * <p>É condição de negócio, não falha de infraestrutura: o período existe, o preço não.
 * Por isso 409 (conflito com o estado do catálogo) e não 503.</p>
 */
public class RateUnavailableException extends CustomException {

    public RateUnavailableException(final String message) {
        super(message, Response.Status.CONFLICT.getStatusCode());
    }
}

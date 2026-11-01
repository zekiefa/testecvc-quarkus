package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;

/**
 * Entrada inválida na borda (hoje: datas fora do formato, contagens de hóspedes
 * inválidas, período inconsistente ou fora do limite). Resulta em HTTP 400.
 */
public class InvalidRequestException extends CustomException {
    public InvalidRequestException(final String message) {
        super(message, Response.Status.BAD_REQUEST.getStatusCode());
    }
}

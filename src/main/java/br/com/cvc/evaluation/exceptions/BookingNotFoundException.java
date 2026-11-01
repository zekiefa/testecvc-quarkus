package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;

/**
 * Reserva inexistente — ou existente, mas de outro dono.
 *
 * <p>Os dois casos respondem 404 de propósito: 403 confirmaria que aquele id existe,
 * o que já é informação sobre a reserva de outra pessoa.</p>
 */
public class BookingNotFoundException extends CustomException {
    public BookingNotFoundException(final String message) {
        super(message, Response.Status.NOT_FOUND.getStatusCode());
    }
}

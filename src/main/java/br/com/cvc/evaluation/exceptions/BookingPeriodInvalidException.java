package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;

public class BookingPeriodInvalidException extends CustomException {
    public BookingPeriodInvalidException(final String message) {
        super(message, Response.Status.BAD_REQUEST.getStatusCode());
    }
}

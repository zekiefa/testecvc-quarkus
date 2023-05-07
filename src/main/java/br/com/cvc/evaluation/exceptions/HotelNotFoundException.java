package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;

public class HotelNotFoundException extends CustomException {

    public HotelNotFoundException(final String message) {
        super(message, Response.Status.NOT_FOUND.getStatusCode());
    }
}

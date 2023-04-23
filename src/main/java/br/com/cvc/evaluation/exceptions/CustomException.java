package br.com.cvc.evaluation.exceptions;

import java.io.Serializable;

public class CustomException
                extends RuntimeException
                implements Serializable {
    private final int statusCode;

    public CustomException(final String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatus() {
        return this.statusCode;
    }
}

package br.com.cvc.evaluation.exceptions;

import java.io.Serializable;

public class CustomException
                extends RuntimeException
                implements Serializable {
    private final int statusCode;

    /**
     * Normaliza a mensagem <b>antes</b> de chamar {@code super} — corpo de construtor flexível
     * (Java 25). Antes disso só era possível com um método estático auxiliar.
     *
     * <p>Mensagem vazia em erro vira detalhe inútil no problem+json e no log; aqui ela ganha
     * um texto padrão.</p>
     */
    public CustomException(final String message, final int statusCode) {
        final var detail = message == null || message.isBlank() ? "Request could not be processed" : message;
        super(detail);
        this.statusCode = statusCode;
    }

    public int getStatus() {
        return this.statusCode;
    }
}

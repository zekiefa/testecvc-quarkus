package br.com.cvc.evaluation.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Erro com mensagem vazia produz {@code detail} inútil no problem+json e no log.
 *
 * <p>A normalização acontece antes do {@code super()} — corpo de construtor flexível do
 * Java 25. Este teste cobre os três casos: mensagem real, em branco e nula.</p>
 */
class CustomExceptionTest {

    @Test
    @DisplayName("Mensagem real é preservada")
    void keepsTheRealMessage() {
        assertEquals("boom", new InvalidRequestException("boom").getMessage());
    }

    @Test
    @DisplayName("Mensagem em branco vira texto padrão")
    void blankMessageBecomesDefault() {
        assertEquals("Request could not be processed", new InvalidRequestException("   ").getMessage());
    }

    @Test
    @DisplayName("Mensagem nula vira texto padrão, sem estourar NPE")
    void nullMessageBecomesDefault() {
        final var exception = new InvalidRequestException(null);

        assertEquals("Request could not be processed", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("O status HTTP vem da subclasse, não da mensagem")
    void statusComesFromTheSubclass() {
        assertEquals(400, new InvalidRequestException("x").getStatus());
        assertEquals(404, new HotelNotFoundException("x").getStatus());
        assertEquals(409, new RoomUnavailableException("x").getStatus());
    }
}

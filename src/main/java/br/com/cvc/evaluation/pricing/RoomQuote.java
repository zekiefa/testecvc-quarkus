package br.com.cvc.evaluation.pricing;

import java.math.BigDecimal;

/**
 * Resultado do cálculo de preço de um quarto para um período.
 *
 * <p>{@code pricePerDayAdult} e {@code pricePerDayChild} são diárias <b>com</b> taxa inclusa.
 * {@code totalAmount} é o total do quarto no período, e por invariante deve ser igual a
 * {@code (pricePerDayAdult * adults + pricePerDayChild * children) * nights}
 * (ver ADR-004 e {@code PricingCalculatorTest}).</p>
 */
public record RoomQuote(BigDecimal totalAmount,
                        BigDecimal pricePerDayAdult,
                        BigDecimal pricePerDayChild,
                        long nights) {
}

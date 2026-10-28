package br.com.cvc.evaluation.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes do cálculo de preço.
 *
 * <p>Deliberadamente <b>sem</b> {@code @QuarkusTest}: o cálculo é puro, então o teste deve
 * ser rápido e não depender de CDI, broker ou banco.</p>
 *
 * <p>{@code FEE_BPS} é valor de <b>teste</b>, não regra de negócio: o valor comercial da taxa
 * ainda está em aberto (ADR-004, {@code TODO(PRODUTO)}). Estes testes fixam a <b>fórmula</b>.</p>
 */
class PricingCalculatorTest {
    private static final int FEE_BPS = 700; // 7,00%

    @Test
    @DisplayName("1 noite, 1 adulto + 1 criança: diárias e total com taxa")
    void oneNightIncludesFee() {
        final var quote = PricingCalculator.quote(money("900.00"), money("450.00"), 1, 1, 1, FEE_BPS);

        assertEquals(money("963.00"), quote.pricePerDayAdult(), "900.00 + 7% = 963.00");
        assertEquals(money("481.50"), quote.pricePerDayChild(), "450.00 + 7% = 481.50");
        assertEquals(money("1444.50"), quote.totalAmount());
        assertEquals(1L, quote.nights());
    }

    @Test
    @DisplayName("3 noites: o total inclui a taxa (regressão do bug em que o total a excluía)")
    void threeNightsIncludeFeeInTotal() {
        final var quote = PricingCalculator.quote(money("900.00"), money("450.00"), 1, 1, 3, FEE_BPS);

        assertEquals(money("4333.50"), quote.totalAmount(), "(963.00 + 481.50) * 3");
        assertNotEquals(money("4050.00"), quote.totalAmount(),
                        "4050.00 era o valor antigo, calculado sem a taxa");
    }

    @Test
    @DisplayName("Total fecha exatamente com as diárias informadas (invariante do ADR-004)")
    void totalMatchesNightlyRates() {
        final var adults = 2;
        final var children = 1;
        final var nights = 4L;
        final var quote = PricingCalculator.quote(money("900.00"), money("450.00"), adults, children, nights, FEE_BPS);

        final var expected = quote.pricePerDayAdult().multiply(BigDecimal.valueOf(adults))
                        .add(quote.pricePerDayChild().multiply(BigDecimal.valueOf(children)))
                        .multiply(BigDecimal.valueOf(nights))
                        .setScale(2, RoundingMode.HALF_UP);

        assertEquals(expected, quote.totalAmount());
    }

    @Test
    @DisplayName("Arredondamento HALF_UP por diária (nunca no total)")
    void roundsHalfUpPerNightlyRate() {
        // 259.67 * 1.07 = 277.8469 -> 277.85
        assertEquals(money("277.85"), PricingCalculator.nightlyRate(money("259.67"), FEE_BPS));
    }

    @Test
    @DisplayName("Sem crianças, a diária de criança é zero e não entra no total")
    void zeroChildrenChargesNoChildRate() {
        final var quote = PricingCalculator.quote(money("900.00"), null, 1, 0, 2, FEE_BPS);

        assertEquals(money("0.00"), quote.pricePerDayChild());
        assertEquals(money("1926.00"), quote.totalAmount());
    }

    @Test
    @DisplayName("Taxa zero usa a tarifa base")
    void zeroFeeUsesBaseRate() {
        assertEquals(money("900.00"), PricingCalculator.nightlyRate(money("900.00"), 0));
    }

    @Test
    @DisplayName("Basis points fracionários são aceitos (275 = 2,75%)")
    void acceptsFractionalBasisPoints() {
        // 900.00 * 1.0275 = 924.75
        assertEquals(money("924.75"), PricingCalculator.nightlyRate(money("900.00"), 275));
    }

    @Test
    void rejectsNegativeFee() {
        assertThrows(IllegalArgumentException.class, () -> PricingCalculator.nightlyRate(money("900.00"), -1));
    }

    @Test
    void rejectsZeroNights() {
        assertThrows(IllegalArgumentException.class,
                        () -> PricingCalculator.quote(money("900.00"), money("450.00"), 1, 1, 0, FEE_BPS));
    }

    @Test
    void rejectsQuoteWithoutPax() {
        assertThrows(IllegalArgumentException.class,
                        () -> PricingCalculator.quote(money("900.00"), money("450.00"), 0, 0, 1, FEE_BPS));
    }

    private static BigDecimal money(final String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}

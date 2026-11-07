package br.com.cvc.evaluation.pricing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fixa a <b>fórmula</b> do ADR-004, não o valor comercial: a taxa é parâmetro
 * (configuração atual: 1176 bps = 11,76%).
 *
 * <p>Testa o que a produção usa — {@code nightlyRate} e {@code nightlyTotal} — e não um
 * atalho que existia só para o teste anterior.</p>
 */
class PricingCalculatorTest {
    private static final int FEE_BPS = 1176; // 11,76%

    @Test
    @DisplayName("Diária com taxa: 900,00 e 450,00 sobem para 1005,84 e 502,92")
    void nightlyRateAppliesFee() {
        assertAll("nightly",
                        () -> assertEquals(money("1005.84"),
                                        PricingCalculator.nightlyRate(money("900.00"), FEE_BPS),
                                        "900.00 + 11,76% = 1005.84"),
                        () -> assertEquals(money("502.92"),
                                        PricingCalculator.nightlyRate(money("450.00"), FEE_BPS),
                                        "450.00 + 11,76% = 502.92"));
    }

    @Test
    @DisplayName("Noite com 1 adulto + 1 criança soma as duas diárias")
    void nightlyTotalAddsBothPax() {
        final var adult = PricingCalculator.nightlyRate(money("900.00"), FEE_BPS);
        final var child = PricingCalculator.nightlyRate(money("450.00"), FEE_BPS);

        assertEquals(money("1508.76"), PricingCalculator.nightlyTotal(adult, child, 1, 1));
    }

    @Test
    @DisplayName("Três noites: total é a soma das noites, com a taxa incluída")
    void threeNightsIncludeFee() {
        final var adult = PricingCalculator.nightlyRate(money("900.00"), FEE_BPS);
        final var child = PricingCalculator.nightlyRate(money("450.00"), FEE_BPS);
        final var night = PricingCalculator.nightlyTotal(adult, child, 1, 1);

        final var total = night.multiply(BigDecimal.valueOf(3)).setScale(2, RoundingMode.HALF_UP);

        assertAll("three-nights",
                        () -> assertEquals(money("4526.28"), total, "(1005.84 + 502.92) * 3"),
                        () -> assertTrue(new BigDecimal("4050.00").compareTo(total) != 0,
                                        "4050.00 era o valor antigo, calculado sem a taxa"));
    }

    @Test
    @DisplayName("Criança sem tarifa informada não inventa preço")
    void childWithoutRateIsZeroed() {
        final var adult = PricingCalculator.nightlyRate(money("900.00"), FEE_BPS);
        final var zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 1 adulto + 1 criança, mas sem tarifa infantil: a criança não acrescenta nada
        assertEquals(money("1005.84"), PricingCalculator.nightlyTotal(adult, zero, 1, 1));
        assertEquals(zero, PricingCalculator.nightlyTotal(zero, zero, 0, 1), "sem tarifa e sem adulto, zero");
    }

    @Test
    @DisplayName("Taxa zero devolve a tarifa base, sem alterar escala")
    void zeroFeeKeepsBaseRate() {
        final var rate = PricingCalculator.nightlyRate(money("900.00"), 0);

        assertEquals(money("900.00"), rate);
        assertEquals(2, rate.scale(), "escala monetária é sempre 2");
    }

    @Test
    @DisplayName("Arredondamento é HALF_UP na diária, não no total")
    void roundsHalfUpPerNightlyRate() {
        // 259.67 * 1.1176 = 290.2072... -> 290.21
        assertEquals(money("290.21"), PricingCalculator.nightlyRate(money("259.67"), FEE_BPS));
    }

    @Test
    @DisplayName("Basis points fracionários são aceitos (275 = 2,75%)")
    void acceptsFractionalBasisPoints() {
        assertEquals(money("924.75"), PricingCalculator.nightlyRate(money("900.00"), 275));
    }

    @Test
    void rejectsNegativeFee() {
        assertThrows(IllegalArgumentException.class,
                        () -> PricingCalculator.nightlyRate(money("900.00"), -1));
    }

    @Test
    void rejectsNullRate() {
        assertThrows(NullPointerException.class, () -> PricingCalculator.nightlyRate(null, FEE_BPS));
    }

    @Test
    @DisplayName("Sem pax, a noite custa zero — nunca um valor inventado")
    void zeroPaxCostsZero() {
        final var adult = PricingCalculator.nightlyRate(money("900.00"), FEE_BPS);

        assertEquals(money("0.00"), PricingCalculator.nightlyTotal(adult, adult, 0, 0));
    }

    private static BigDecimal money(final String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}

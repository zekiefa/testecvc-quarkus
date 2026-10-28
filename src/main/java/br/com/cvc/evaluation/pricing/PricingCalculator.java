package br.com.cvc.evaluation.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Cálculo de preço da reserva — deliberadamente puro: sem CDI, sem I/O, sem banco.
 *
 * <p>Regra única (ADR-004):</p>
 * <pre>
 *   diária por pax = arredonda(tarifa base * (1 + taxaBps/10000), 2, HALF_UP)
 *   total          = (diáriaAdulto * adultos + diáriaCrianca * criancas) * noites
 * </pre>
 *
 * <p>A taxa é recebida em <b>basis points</b> (700 = 7,00%) para evitar a ambiguidade
 * de frações como {@code 0.70}. O valor comercial está em aberto — ver ADR-004.</p>
 *
 * <p>Invariante: o total nunca é arredondado depois de somado; cada diária é arredondada
 * uma única vez, de modo que o total feche exatamente com o detalhamento exibido.</p>
 */
public final class PricingCalculator {
    /** Escala monetária: 2 casas. */
    private static final int MONEY_SCALE = 2;
    /** Arredondamento monetário: meio para cima. */
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    /** Escala intermediária do fator de taxa (evita dízima na divisão). */
    private static final int FACTOR_SCALE = 6;
    private static final int BASIS_POINTS_DIVISOR = 10_000;

    private PricingCalculator() {
    }

    /**
     * Calcula uma diária de um pax, já com a taxa aplicada.
     *
     * @param baseRate tarifa base por diária
     * @param feeBps   taxa em basis points (700 = 7,00%); não pode ser negativa
     * @return diária com escala 2, arredondada com {@link RoundingMode#HALF_UP}
     */
    public static BigDecimal nightlyRate(final BigDecimal baseRate, final int feeBps) {
        Objects.requireNonNull(baseRate, "baseRate must not be null");
        if (feeBps < 0) {
            throw new IllegalArgumentException("feeBps must not be negative: " + feeBps);
        }

        final var feeFactor = BigDecimal.valueOf(feeBps)
                        .divide(BigDecimal.valueOf(BASIS_POINTS_DIVISOR), FACTOR_SCALE, MONEY_ROUNDING);

        return baseRate.multiply(BigDecimal.ONE.add(feeFactor)).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    /**
     * Soma as diárias de todos os pax de uma noite.
     */
    public static BigDecimal nightlyTotal(final BigDecimal adultNightlyRate,
                    final BigDecimal childNightlyRate,
                    final int adults,
                    final int children) {
        return adultNightlyRate.multiply(BigDecimal.valueOf(adults))
                        .add(childNightlyRate.multiply(BigDecimal.valueOf(children)))
                        .setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    /**
     * Calcula o preço completo de um quarto para um período.
     *
     * @param adultBaseRate tarifa base do adulto (obrigatória quando {@code adults > 0})
     * @param childBaseRate tarifa base da criança (obrigatória quando {@code children > 0})
     * @param adults        quantidade de adultos ({@code >= 0})
     * @param children      quantidade de crianças ({@code >= 0})
     * @param nights        noites do período ({@code >= 1})
     * @param feeBps        taxa em basis points ({@code >= 0})
     */
    public static RoomQuote quote(final BigDecimal adultBaseRate,
                    final BigDecimal childBaseRate,
                    final int adults,
                    final int children,
                    final long nights,
                    final int feeBps) {
        if (adults < 0 || children < 0) {
            throw new IllegalArgumentException("adults and children must not be negative");
        }
        if (nights < 1) {
            throw new IllegalArgumentException("nights must be at least 1: " + nights);
        }
        if (adults == 0 && children == 0) {
            throw new IllegalArgumentException("at least one pax is required");
        }

        final var adultNightlyRate = adults > 0
                        ? nightlyRate(adultBaseRate, feeBps)
                        : BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
        final var childNightlyRate = children > 0
                        ? nightlyRate(childBaseRate, feeBps)
                        : BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);

        final var nightTotal = nightlyTotal(adultNightlyRate, childNightlyRate, adults, children);
        final var total = nightTotal.multiply(BigDecimal.valueOf(nights)).setScale(MONEY_SCALE, MONEY_ROUNDING);

        return new RoomQuote(total, adultNightlyRate, childNightlyRate, nights);
    }
}

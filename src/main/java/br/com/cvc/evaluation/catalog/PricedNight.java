package br.com.cvc.evaluation.catalog;

import java.math.BigDecimal;

/** Noite com a nossa taxa já aplicada, do jeito que vai para o hóspede. */
public record PricedNight(String date, BigDecimal adult, BigDecimal child) {
}

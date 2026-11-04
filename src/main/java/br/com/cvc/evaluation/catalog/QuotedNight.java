package br.com.cvc.evaluation.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Noite cotada pelo broker, em valor líquido (sem a nossa taxa). */
public record QuotedNight(LocalDate date, BigDecimal adult, BigDecimal child) {
}

package br.com.cvc.evaluation.broker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Noite líquida devolvida pelo broker — a mesma em cotação e em busca. */
public record BrokerNight(LocalDate date, BigDecimal adult, BigDecimal child) {
}

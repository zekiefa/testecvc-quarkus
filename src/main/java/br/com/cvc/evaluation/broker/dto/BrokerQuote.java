package br.com.cvc.evaluation.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cotação devolvida pelo broker. Os valores são <b>líquidos</b>: a taxa é aplicada por nós.
 * {@code staleAfter} diz até quando o broker considera o dado válido.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrokerQuote(String currency, BigDecimal totalAmount, List<Night> nights, Instant updatedAt,
                Instant staleAfter, String dataSource) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Night(LocalDate date, BigDecimal adult, BigDecimal child) {
    }
}

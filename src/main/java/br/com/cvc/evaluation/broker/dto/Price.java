package br.com.cvc.evaluation.broker.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Price(BigDecimal adult, BigDecimal child) {
}

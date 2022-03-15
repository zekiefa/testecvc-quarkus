package br.com.cvc.evaluation.service;

import java.math.BigDecimal;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "booking")
public interface FeeProvider {
    BigDecimal fee();
}

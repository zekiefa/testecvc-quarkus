package br.com.cvc.evaluation.service.provider;

import java.math.BigDecimal;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "booking")
public interface FeeProvider {
    BigDecimal fee();
}

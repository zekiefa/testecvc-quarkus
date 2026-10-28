package br.com.cvc.evaluation.service.provider;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "booking")
public interface FeeProvider {
    /**
     * Taxa aplicada sobre a tarifa base, em <b>basis points</b> (700 = 7,00%).
     *
     * <p>A unidade é decisão fechada (ADR-004); o <b>valor</b> ainda está em aberto:
     * {@code 700} é apenas o valor provisório de teste local, marcado como TODO(PRODUTO).
     * Use frações de basis points para qualquer outro valor (ex.: 275 = 2,75%).</p>
     */
    int feeBps();
}

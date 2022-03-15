package br.com.cvc.evaluation.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.math.BigDecimal;

@ApplicationScoped
public class FeeService {
    // o ideal é que a comissão seja parametrizada via banco de dados
    // ou disponibilizada por um serviço externo
    @Inject
    FeeProvider provider;

    public BigDecimal calculateFee(final BigDecimal paxPrice, final Long days) {
        final var totalPricePax = paxPrice.multiply(BigDecimal.valueOf(days));

        return totalPricePax.multiply(this.provider.fee());
    }
}

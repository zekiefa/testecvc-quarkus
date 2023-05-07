package br.com.cvc.evaluation.service;

import java.math.BigDecimal;

import br.com.cvc.evaluation.service.provider.FeeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class FeeService {
    private static final Logger log = LoggerFactory.getLogger(FeeService.class);
    // o ideal é que a comissão seja parametrizada via banco de dados
    // ou disponibilizada por um serviço externo
    @Inject
    FeeProvider provider;

    public BigDecimal calculateFee(final BigDecimal paxPrice, final Long days) {
        log.info("Calculating fee: pax price {} for {} days", paxPrice, days);
        final var totalPricePax = paxPrice.multiply(BigDecimal.valueOf(days));
        final var fee = totalPricePax.multiply(this.provider.fee());

        log.info("Fee calculated: {}", fee);
        return fee;
    }
}

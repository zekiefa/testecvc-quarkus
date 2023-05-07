package br.com.cvc.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.DayOfWeek;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FeeServiceTest {
    @Inject
    FeeService feeService;

    @InjectSpy
    FeeProvider feeProvider;

    @Test
    void testCalculateFee() {
        // Arranges
        final var paxPrice = BigDecimal.valueOf(259.67);
        final var days = Long.valueOf(DayOfWeek.values().length);

        // Act
        final var result = feeService.calculateFee(paxPrice, days);

        // Assert
        assertEquals(0, paxPrice.multiply(BigDecimal.valueOf(days))
                        .multiply(feeProvider.fee()).compareTo(result));
    }

    @ApplicationScoped
    public static class FeeProvider {
        public BigDecimal fee() {
            return BigDecimal.valueOf(0.70);
        }
    }
}

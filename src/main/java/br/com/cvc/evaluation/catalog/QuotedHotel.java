package br.com.cvc.evaluation.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Oferta do broker para um hotel no período, em valores líquidos (sem a nossa taxa). */
public record QuotedHotel(Integer hotelId, String hotelName, String cityName, Integer roomId, String roomTypeName,
                Integer ratePlanId, String ratePlanName, String board, String cancellationPolicy, String currency,
                String dataSource, List<Night> nights) {

    /** Noite líquida cotada pelo broker. */
    public record Night(LocalDate date, BigDecimal adult, BigDecimal child) {
    }
}

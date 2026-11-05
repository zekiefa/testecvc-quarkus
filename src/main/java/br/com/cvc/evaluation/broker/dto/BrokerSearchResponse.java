package br.com.cvc.evaluation.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Resposta do {@code GET /v1/search} do broker: um item por hotel, com as noites do plano vencedor. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrokerSearchResponse(String cityCode, LocalDate checkIn, LocalDate checkOut, int adults, int children,
                List<Hit> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(Long hotelId, String hotelName, String cityCode, String cityName, Long roomTypeId,
                    String roomTypeName, Long ratePlanId, String ratePlanName, String board,
                    String cancellationPolicy, BigDecimal totalAmount, String currency, Instant updatedAt,
                    Instant staleAfter, String dataSource, List<Night> nights) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Night(LocalDate date, BigDecimal adult, BigDecimal child) {
    }
}

package br.com.cvc.evaluation.broker.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Resposta do {@code GET /v1/hotels/{id}} do broker: catálogo sem preço (preço vem da cotação). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrokerHotelDetail(Long id, String name, String cityCode, String cityName, BigDecimal latitude,
                BigDecimal longitude, String dataSource, List<Room> roomTypes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Room(Long id, String name, int maxAdults, int maxChildren, List<Plan> ratePlans) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Plan(Long id, String name, String board, String cancellationPolicy) {
    }
}

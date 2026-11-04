package br.com.cvc.evaluation.broker.dto;

import java.time.LocalDate;

/** Corpo do {@code POST /v1/quotes} do broker. */
public record BrokerQuoteRequest(Long hotelId, Long roomTypeId, Long ratePlanId, LocalDate checkIn,
                LocalDate checkOut, Integer adults, Integer children) {
}

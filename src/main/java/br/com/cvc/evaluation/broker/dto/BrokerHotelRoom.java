package br.com.cvc.evaluation.broker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrokerHotelRoom(Integer roomID, String categoryName, Price price) {
	}

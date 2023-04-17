package br.com.cvc.evaluation.broker.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrokerHotel(Integer id, String name, Integer cityCode, String cityName, List<BrokerHotelRoom> rooms) {

}

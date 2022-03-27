package br.com.cvc.evaluation.broker.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrokerHotel {
	private Integer id;
	private String name;
	private Integer cityCode;
	private String cityName;
	private List<BrokerHotelRoom> rooms;
}

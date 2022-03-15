package br.com.cvc.evaluation.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Hotel {
	private Integer id;
	private String cityName;
	private String name;
	private List<Room> rooms;
}

package br.com.cvc.evaluation.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {
	private Integer roomID;
	private String categoryName;
	private BigDecimal totalPrice;
	private PriceDetail priceDetail;
}

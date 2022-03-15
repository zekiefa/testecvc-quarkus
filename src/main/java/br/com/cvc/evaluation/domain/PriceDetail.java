package br.com.cvc.evaluation.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceDetail {
	private BigDecimal pricePerDayAdult;
	private BigDecimal pricePerDayChild;
}

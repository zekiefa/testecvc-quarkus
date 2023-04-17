package br.com.cvc.evaluation.domain;

import java.math.BigDecimal;

public record Room(Integer roomID, String categoryName, BigDecimal totalPrice, PriceDetail priceDetail) {

}

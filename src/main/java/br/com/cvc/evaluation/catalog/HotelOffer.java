package br.com.cvc.evaluation.catalog;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de busca: um hotel com o plano mais barato do período, <b>já com a nossa taxa</b>.
 *
 * <p>A tarifa vem por noite (o broker publica por data), então o detalhamento acompanha a
 * resposta: o hóspede vê exatamente o que será gravado na reserva.</p>
 */
public record HotelOffer(Integer hotelId, String hotelName, String cityName, Integer roomId, String roomTypeName,
                Integer ratePlanId, String ratePlanName, String board, String cancellationPolicy, String currency,
                BigDecimal totalAmount, List<PricedNight> nights) {

    public record PricedNight(String date, BigDecimal adult, BigDecimal child) {
    }
}

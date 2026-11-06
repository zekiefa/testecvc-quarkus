package br.com.cvc.evaluation.catalog;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalhe do hotel para um período: catálogo + cada regime cotado.
 *
 * <p>Preço é por data, então sem período não há preço — e o endpoint passou a exigir um.
 * Regime sem tarifa publicada aparece com {@code available: false}, em vez de sumir: o
 * hóspede precisa saber que existe e não está à venda.</p>
 */
public record HotelDetail(Long hotelId, String hotelName, String cityName, String dataSource,
                List<Room> rooms) {

    public record Room(Long roomId, String name, int maxAdults, int maxChildren, List<Plan> ratePlans) {
    }

    public record Plan(Long ratePlanId, String name, String board, String cancellationPolicy, boolean available,
                    String currency, BigDecimal totalAmount, List<Night> nights) {
    }

    public record Night(String date, BigDecimal adult, BigDecimal child) {
    }
}

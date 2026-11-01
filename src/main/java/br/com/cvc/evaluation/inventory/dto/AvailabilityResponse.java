package br.com.cvc.evaluation.inventory.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Disponibilidade por quarto e por noite.
 *
 * <p>{@code availableRooms} é o mínimo entre as noites: é o número que pode ser vendido
 * para o período inteiro. O detalhe por noite fica exposto para o cliente entender
 * exatamente qual data trava a venda.</p>
 */
public record AvailabilityResponse(Integer hotelId,
                LocalDate checkIn,
                LocalDate checkOut,
                List<Room> rooms) {

    public record Room(Integer roomId, String categoryName, int availableRooms, List<Night> nights) {
    }

    public record Night(String date, int available) {
    }
}

package br.com.cvc.evaluation.booking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do POST /api/v1/bookings. A validação estrutural fica nas anotações (400);
 * as regras de negócio (período máximo, tarifa, estoque) ficam no caso de uso.
 */
public record CreateBookingRequest(
                @NotNull Integer hotelId,
                @NotNull Integer roomId,
                @NotNull LocalDate checkIn,
                @NotNull LocalDate checkOut,
                @NotNull @Min(1) Integer adults,
                @NotNull @Min(0) @Max(10) Integer children) {
}

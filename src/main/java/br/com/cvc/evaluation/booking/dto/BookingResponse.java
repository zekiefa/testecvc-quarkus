package br.com.cvc.evaluation.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.cvc.evaluation.booking.Booking;
import br.com.cvc.evaluation.booking.NightlyRate;

public record BookingResponse(UUID id,
                Integer hotelId,
                Integer roomId,
                LocalDate checkIn,
                LocalDate checkOut,
                int adults,
                int children,
                String status,
                String currency,
                BigDecimal totalAmount,
                List<NightlyRate> nightlyBreakdown,
                Instant createdAt) {

    public static BookingResponse from(final Booking booking) {
        return new BookingResponse(booking.id,
                        booking.hotelId,
                        booking.roomId,
                        booking.checkIn,
                        booking.checkOut,
                        booking.adults,
                        booking.children,
                        booking.status.name(),
                        booking.currency,
                        booking.totalAmount,
                        List.copyOf(booking.nightlyBreakdown),
                        booking.createdAt);
    }
}

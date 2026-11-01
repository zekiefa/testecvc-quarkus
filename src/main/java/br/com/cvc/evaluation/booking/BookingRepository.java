package br.com.cvc.evaluation.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

@ApplicationScoped
public class BookingRepository implements PanacheRepositoryBase<Booking, UUID> {

    /** Usado pelo caso de uso de criação para responder de forma idempotente (ADR-003). */
    public Optional<Booking> findByIdempotencyKey(final String idempotencyKey) {
        return find("idempotencyKey", idempotencyKey).firstResultOptional();
    }

    public Optional<Booking> findBookingById(final UUID id) {
        return findByIdOptional(id);
    }

    public List<Booking> findByHotelAndRoom(final Integer hotelId, final Integer roomId) {
        return list("hotelId = ?1 and roomId = ?2 order by checkIn", hotelId, roomId);
    }

    /** Reservas ativas que terminam antes de uma data — base para a rotina de NO_SHOW (M2). */
    public List<Booking> findActiveEndingBefore(final LocalDate date) {
        return list("status in ?1 and checkOut < ?2",
                        List.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN), date);
    }
}

package br.com.cvc.evaluation.booking;

import java.util.Map;
import java.util.UUID;

import br.com.cvc.evaluation.exceptions.BookingNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Leitura e cancelamento da reserva. Toda operação passa pela checagem de dono
 * (ADR-003): quem não criou a reserva recebe 404, não 403 — não confirmamos existência.
 */
@ApplicationScoped
public class BookingQueryService {

    private final BookingRepository repository;

    public BookingQueryService(final BookingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Booking getOwned(final UUID id, final String subject) {
        return this.repository.findBookingById(id)
                        .filter(booking -> booking.isOwnedBy(subject))
                        .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + id));
    }

    @Transactional
    public Booking cancel(final UUID id, final String subject) {
        final var booking = this.getOwned(id, subject);

        // Lança InvalidBookingStateException (409) se o estado não permitir cancelar.
        booking.transitionTo(BookingStatus.CANCELLED);

        BookingEvent.of(booking, "CANCELLED", Map.of("subject", subject)).persist();
        this.repository.flush();

        return booking;
    }
}

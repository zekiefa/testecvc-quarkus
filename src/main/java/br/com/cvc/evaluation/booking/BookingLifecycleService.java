package br.com.cvc.evaluation.booking;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import br.com.cvc.evaluation.exceptions.InvalidBookingStateException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Transições operacionais da reserva: check-in, check-out e no-show.
 *
 * <p>A data de hoje entra como parâmetro (vinda do endpoint) para que a regra seja
 * testável sem relógio mágico. As transições em si são validadas pelo grafo de
 * {@link BookingStatus} — aqui só ficam as regras de calendário.</p>
 */
@ApplicationScoped
public class BookingLifecycleService {

    private final BookingRepository repository;
    private final BookingQueryService queryService;

    public BookingLifecycleService(final BookingRepository repository, final BookingQueryService queryService) {
        this.repository = repository;
        this.queryService = queryService;
    }

    @Transactional
    public Booking checkIn(final UUID id, final String subject, final LocalDate today) {
        final var booking = this.queryService.getOwned(id, subject);

        if (today.isBefore(booking.checkIn)) {
            throw new InvalidBookingStateException("Check-in opens on " + booking.checkIn);
        }
        if (today.isAfter(booking.checkOut)) {
            throw new InvalidBookingStateException("Booking period ended on " + booking.checkOut);
        }

        booking.transitionTo(BookingStatus.CHECKED_IN);
        return this.record(booking, "CHECKED_IN", subject);
    }

    @Transactional
    public Booking checkOut(final UUID id, final String subject, final LocalDate today) {
        final var booking = this.queryService.getOwned(id, subject);

        if (today.isBefore(booking.checkIn)) {
            throw new InvalidBookingStateException("Check-out cannot happen before " + booking.checkIn);
        }

        booking.transitionTo(BookingStatus.CHECKED_OUT);
        return this.record(booking, "CHECKED_OUT", subject);
    }

    /**
     * No-show: só depois da data de check-in e sem check-in registrado. O inventário
     * <b>não</b> volta — a noite foi vendida e não foi usufruída.
     */
    @Transactional
    public Booking noShow(final UUID id, final String subject, final LocalDate today) {
        final var booking = this.queryService.getOwned(id, subject);

        if (!today.isAfter(booking.checkIn)) {
            throw new InvalidBookingStateException("No-show can only be recorded after " + booking.checkIn);
        }

        booking.transitionTo(BookingStatus.NO_SHOW);
        return this.record(booking, "NO_SHOW", subject);
    }

    private Booking record(final Booking booking, final String type, final String subject) {
        BookingEvent.of(booking, type, Map.of("subject", subject)).persist();
        this.repository.flush();
        return booking;
    }
}

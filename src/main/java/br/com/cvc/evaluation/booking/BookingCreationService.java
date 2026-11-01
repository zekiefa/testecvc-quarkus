package br.com.cvc.evaluation.booking;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import br.com.cvc.evaluation.booking.dto.CreateBookingRequest;
import br.com.cvc.evaluation.broker.BrokerService;
import br.com.cvc.evaluation.broker.dto.BrokerHotelRoom;
import br.com.cvc.evaluation.exceptions.BookingPeriodInvalidException;
import br.com.cvc.evaluation.exceptions.BrokerUnavailableException;
import br.com.cvc.evaluation.exceptions.HotelNotFoundException;
import br.com.cvc.evaluation.exceptions.IdempotencyConflictException;
import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import br.com.cvc.evaluation.pricing.PricingCalculator;
import br.com.cvc.evaluation.pricing.RoomQuote;
import br.com.cvc.evaluation.service.provider.BookingSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caso de uso de criação da reserva (docs/arquitetura.md §3.6).
 *
 * <p>A tarifa é lida do broker no momento da criação e congelada no snapshot; o broker
 * nunca é consultado de novo para uma reserva já criada (ADR-002).</p>
 *
 * <p>Idempotência: a chave identifica a intenção. Replay com a mesma chave e o mesmo dono
 * devolve a reserva existente; mesma chave com outro dono é conflito — devolver a reserva
 * alheia vazaria dados de terceiros. A corrida entre duas requisições simultâneas é
 * tratada no M1.6.</p>
 */
@ApplicationScoped
public class BookingCreationService {
    private static final Logger log = LoggerFactory.getLogger(BookingCreationService.class);
    private static final long MAX_NIGHTS = 30L;

    private final BookingRepository repository;
    private final BrokerService broker;
    private final BookingSettings settings;

    public BookingCreationService(final BookingRepository repository,
                    @RestClient final BrokerService broker,
                    final BookingSettings settings) {
        this.repository = repository;
        this.broker = broker;
        this.settings = settings;
    }

    public record CreationResult(Booking booking, boolean created) {
    }

    @Transactional
    public CreationResult create(final CreateBookingRequest request, final String idempotencyKey,
                    final String subject) {
        final var existing = this.repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return this.replay(existing.get(), subject);
        }

        final var nights = this.validatePeriod(request.checkIn(), request.checkOut());
        final var room = this.loadRoom(request);
        final var quote = this.quote(room, request, nights);

        final var booking = Booking.create(idempotencyKey,
                        subject,
                        request.hotelId(),
                        request.roomId(),
                        request.checkIn(),
                        request.checkOut(),
                        request.adults(),
                        request.children(),
                        this.settings.currency(),
                        quote.totalAmount(),
                        this.breakdown(request.checkIn(), nights, quote));

        this.repository.persist(booking);
        BookingEvent.of(booking, "CREATED", Map.of("idempotencyKey", idempotencyKey, "subject", subject)).persist();
        this.repository.flush();

        log.info("Booking created: {} (hotel {}, room {}, {} nights)", booking.id, booking.hotelId, booking.roomId,
                        nights);
        return new CreationResult(booking, true);
    }

    private CreationResult replay(final Booking booking, final String subject) {
        if (!Objects.equals(booking.userSubject, subject)) {
            throw new IdempotencyConflictException("Idempotency-Key already used by another user");
        }
        log.info("Idempotent replay for key {} -> booking {}", booking.idempotencyKey, booking.id);
        return new CreationResult(booking, false);
    }

    private long validatePeriod(final LocalDate checkIn, final LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new BookingPeriodInvalidException("Checkout must be after checkin");
        }
        final var nights = checkIn.until(checkOut, ChronoUnit.DAYS);
        if (nights > MAX_NIGHTS) {
            throw new InvalidRequestException("Period must not exceed " + MAX_NIGHTS + " nights: " + nights);
        }
        return nights;
    }

    private BrokerHotelRoom loadRoom(final CreateBookingRequest request) {
        try {
            final var hotel = this.broker.getHotelDetails(request.hotelId());
            if (hotel == null || hotel.rooms() == null) {
                throw new HotelNotFoundException("Hotel not found: " + request.hotelId());
            }
            return hotel.rooms().stream()
                            .filter(room -> Objects.equals(room.roomID(), request.roomId()))
                            .findFirst()
                            .orElseThrow(() -> new HotelNotFoundException(
                                            "Room " + request.roomId() + " not found in hotel " + request.hotelId()));
        } catch (final WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new HotelNotFoundException("Hotel not found: " + request.hotelId());
            }
            throw new BrokerUnavailableException("Broker failed: " + e.getMessage());
        } catch (final ProcessingException e) {
            throw new BrokerUnavailableException("Broker unreachable: " + e.getMessage());
        }
    }

    private RoomQuote quote(final BrokerHotelRoom room, final CreateBookingRequest request, final long nights) {
        return PricingCalculator.quote(room.price().adult(),
                        room.price().child(),
                        request.adults(),
                        request.children(),
                        nights,
                        this.settings.feeBps());
    }

    private ArrayList<NightlyRate> breakdown(final LocalDate checkIn, final long nights, final RoomQuote quote) {
        final var rates = new ArrayList<NightlyRate>();
        for (var offset = 0L; offset < nights; offset++) {
            rates.add(new NightlyRate(checkIn.plusDays(offset).toString(),
                            quote.pricePerDayAdult(),
                            quote.pricePerDayChild()));
        }
        return rates;
    }
}

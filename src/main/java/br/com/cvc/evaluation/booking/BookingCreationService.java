package br.com.cvc.evaluation.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.com.cvc.evaluation.booking.dto.CreateBookingRequest;
import br.com.cvc.evaluation.broker.BrokerService;
import br.com.cvc.evaluation.catalog.BrokerCatalog;
import br.com.cvc.evaluation.inventory.InventoryService;
import br.com.cvc.evaluation.exceptions.BookingPeriodInvalidException;
import br.com.cvc.evaluation.exceptions.BrokerUnavailableException;
import br.com.cvc.evaluation.exceptions.IdempotencyConflictException;
import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import br.com.cvc.evaluation.catalog.QuotedNight;
import br.com.cvc.evaluation.pricing.PricingCalculator;
import br.com.cvc.evaluation.service.provider.BookingSettings;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.narayana.jta.QuarkusTransaction;
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
    private final BrokerCatalog catalog;
    private final InventoryService inventory;
    private final BookingSettings settings;

    public BookingCreationService(final BookingRepository repository,
                    final BrokerCatalog catalog,
                    final InventoryService inventory,
                    final BookingSettings settings) {
        this.repository = repository;
        this.catalog = catalog;
        this.inventory = inventory;
        this.settings = settings;
    }

    public record CreationResult(Booking booking, boolean created) {
    }

    /**
     * Cria a reserva ou devolve a existente.
     *
     * <p>A transação é programática porque a perderora da corrida precisa de uma transação
     * <b>nova</b> para reler: a violação da constraint única inutiliza a transação corrente.</p>
     */
    public CreationResult create(final CreateBookingRequest request, final String idempotencyKey,
                    final String subject) {
        try {
            return QuarkusTransaction.requiringNew().call(() -> this.createOnce(request, idempotencyKey, subject));
        } catch (final RuntimeException failure) {
            final var winner = QuarkusTransaction.requiringNew()
                            .call(() -> this.repository.findByIdempotencyKey(idempotencyKey));
            if (winner.isPresent()) {
                log.info("Concurrent creation for key {}; replaying the winner", idempotencyKey);
                return this.replay(winner.get(), subject);
            }
            throw failure;
        }
    }

    private CreationResult createOnce(final CreateBookingRequest request, final String idempotencyKey,
                    final String subject) {
        final var existing = this.repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return this.replay(existing.get(), subject);
        }

        final var nights = this.validatePeriod(request.checkIn(), request.checkOut());
        final var quotedNights = this.catalog.quote(request.hotelId(), request.roomId(), request.ratePlanId(),
                        request.checkIn(), request.checkOut(), request.adults(), request.children(), nights);
        final var priced = price(quotedNights, request);

        final var booking = Booking.create(idempotencyKey,
                        subject,
                        request.hotelId(),
                        request.roomId(),
                        request.checkIn(),
                        request.checkOut(),
                        request.adults(),
                        request.children(),
                        this.settings.currency(),
                        priced.totalAmount(),
                        new ArrayList<>(priced.breakdown()));

        // Segura as noites na mesma transação: se o insert falhar, o hold volta atrás.
        this.inventory.reserve(request.hotelId(), request.roomId(), request.checkIn(), request.checkOut());

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

    /**
     * Aplica a taxa por noite e monta o detalhamento.
     *
     * <p>Cada noite tem seu próprio preço líquido (o broker publica por data), então a taxa é
     * aplicada noite a noite e o total é a soma — a invariante do ADR-004 continua valendo:
     * total == soma do detalhamento.</p>
     */
    private PricedBooking price(final List<QuotedNight> nights, final CreateBookingRequest request) {
        final var breakdown = new ArrayList<NightlyRate>(nights.size());
        var total = BigDecimal.ZERO;

        for (final var night : nights) {
            final var adult = PricingCalculator.nightlyRate(night.adult(), this.settings.feeBps());
            final var child = PricingCalculator.nightlyRate(night.child(), this.settings.feeBps());
            breakdown.add(new NightlyRate(night.date().toString(), adult, child));
            total = total.add(PricingCalculator.nightlyTotal(adult, child, request.adults(), request.children()));
        }

        return new PricedBooking(total.setScale(2, RoundingMode.HALF_UP), List.copyOf(breakdown));
    }

    private record PricedBooking(BigDecimal totalAmount, List<NightlyRate> breakdown) {
    }
}

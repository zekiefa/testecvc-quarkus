package br.com.cvc.evaluation.inventory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.LongStream;

import br.com.cvc.evaluation.catalog.BrokerCatalog;
import br.com.cvc.evaluation.exceptions.RoomUnavailableException;
import br.com.cvc.evaluation.inventory.dto.AvailabilityResponse;
import br.com.cvc.evaluation.service.provider.BookingSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Inventário que nós vendemos (ADR-002): o broker diz quais quartos existem, nós dizemos
 * quantos ainda há em cada noite.
 */
@ApplicationScoped
public class InventoryService {
    private static final long MAX_NIGHTS = 30L;

    private final InventoryRepository repository;
    private final BrokerCatalog catalog;
    private final BookingSettings settings;

    public InventoryService(final InventoryRepository repository, final BrokerCatalog catalog,
                    final BookingSettings settings) {
        this.repository = repository;
        this.catalog = catalog;
        this.settings = settings;
    }

    /** As noites do período: {@code [checkIn, checkOut)}. */
    public List<LocalDate> nights(final LocalDate checkIn, final LocalDate checkOut) {
        final var total = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (total < 1 || total > MAX_NIGHTS) {
            throw new IllegalArgumentException("Period must be between 1 and " + MAX_NIGHTS + " nights: " + total);
        }
        return LongStream.range(0, total).mapToObj(checkIn::plusDays).toList();
    }

    /** Cria as linhas do período com a capacidade padrão, sem sobrescrever o que já existe. */
    @Transactional
    public void materialize(final Integer hotelId, final Integer roomId, final LocalDate checkIn,
                    final LocalDate checkOut) {
        this.repository.materialize(hotelId, roomId, this.nights(checkIn, checkOut), this.settings.defaultCapacity());
    }

    /**
     * Materializa e reserva todas as noites do período na mesma transação. Se qualquer
     * noite faltar capacidade, a exceção derruba tudo — inclusive a materialização.
     */
    @Transactional
    public void reserve(final Integer hotelId, final Integer roomId, final LocalDate checkIn,
                    final LocalDate checkOut) {
        final var nights = this.nights(checkIn, checkOut);
        this.repository.materialize(hotelId, roomId, nights, this.settings.defaultCapacity());
        for (final var night : nights) {
            if (!this.repository.hold(hotelId, roomId, night)) {
                throw new RoomUnavailableException("Room " + roomId + " of hotel " + hotelId
                                + " is no longer available on " + night);
            }
        }
    }

    /** Devolve as noites ao inventário (cancelamento de reserva ainda não usufruída). */
    @Transactional
    public void release(final Integer hotelId, final Integer roomId, final LocalDate checkIn,
                    final LocalDate checkOut) {
        for (final var night : this.nights(checkIn, checkOut)) {
            this.repository.release(hotelId, roomId, night);
        }
    }

    @Transactional
    public AvailabilityResponse availability(final Integer hotelId, final LocalDate checkIn, final LocalDate checkOut) {
        final var nights = this.nights(checkIn, checkOut);
        final var hotel = this.catalog.hotel(hotelId);

        final var rooms = (hotel.rooms() == null ? List.<br.com.cvc.evaluation.broker.dto.BrokerHotelRoom>of()
                        : hotel.rooms()).stream()
                        .map(room -> {
                            final var available = this.repository.availableByNight(hotelId, room.roomID(), checkIn,
                                            checkOut);
                            final var perNight = nights.stream()
                                            .map(night -> new AvailabilityResponse.Night(night.toString(),
                                                            available.getOrDefault(night,
                                                                            this.settings.defaultCapacity())))
                                            .toList();
                            final var minimum = perNight.stream()
                                            .mapToInt(AvailabilityResponse.Night::available)
                                            .min()
                                            .orElse(0);
                            return new AvailabilityResponse.Room(room.roomID(), room.categoryName(), minimum,
                                            perNight);
                        })
                        .toList();

        return new AvailabilityResponse(hotelId, checkIn, checkOut, rooms);
    }
}

package br.com.cvc.evaluation.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.com.cvc.evaluation.broker.BrokerService;
import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import br.com.cvc.evaluation.broker.dto.BrokerHotelRoom;
import br.com.cvc.evaluation.domain.Hotel;
import br.com.cvc.evaluation.domain.PriceDetail;
import br.com.cvc.evaluation.domain.Room;
import br.com.cvc.evaluation.exceptions.BookingPeriodInvalidException;
import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import br.com.cvc.evaluation.pricing.PricingCalculator;
import br.com.cvc.evaluation.service.provider.FeeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    /** Convenção do detalhe do hotel: preço exibido para 1 adulto + 1 criança, 1 noite. */
    private static final int DETAIL_PAX = 1;
    private static final long DETAIL_NIGHTS = 1L;
    /** Limite de sanidade para uma estadia; evita pedidos absurdos (ex.: 10 anos). */
    private static final long MAX_NIGHTS = 30L;

    private final BrokerService brokerService;
    private final FeeProvider feeProvider;

    public BookingService(@RestClient final BrokerService brokerService,
                    final FeeProvider feeProvider) {
        this.brokerService = brokerService;
        this.feeProvider = feeProvider;
    }

    private long calculatePeriod(final LocalDate checkin, final LocalDate checkout) {
        if (checkin == null || checkout == null) {
            throw new InvalidRequestException("checkin and checkout are required");
        }

        // Checkout igual ao checkin significaria zero noites e preço zero.
        if (!checkout.isAfter(checkin)) {
            throw new BookingPeriodInvalidException("Checkout must be after checkin");
        }

        final var nights = checkin.until(checkout, ChronoUnit.DAYS);
        if (nights > MAX_NIGHTS) {
            throw new InvalidRequestException("Period must not exceed " + MAX_NIGHTS + " nights: " + nights);
        }

        log.info("Calculating period: checkin {}, checkout {} ({} nights)", checkin, checkout, nights);
        return nights;
    }

    private void validateParty(final Integer adults, final Integer children) {
        if (adults == null || adults < 1) {
            throw new InvalidRequestException("adults must be at least 1: " + adults);
        }
        if (children == null || children < 0) {
            throw new InvalidRequestException("children must not be negative: " + children);
        }
    }

    private Room calculateRoom(final BrokerHotelRoom brokerHotelRoom, final long nights, final int adults,
                    final int children) {
        log.info("Calculating room price: room {}, {} nights, {} adults, {} children",
                        brokerHotelRoom.categoryName(), nights, adults, children);

        final var quote = PricingCalculator.quote(brokerHotelRoom.price().adult(),
                        brokerHotelRoom.price().child(),
                        adults,
                        children,
                        nights,
                        this.feeProvider.feeBps());

        log.info("Room total price: {}", quote.totalAmount());
        return new Room(brokerHotelRoom.roomID(),
                        brokerHotelRoom.categoryName(),
                        quote.totalAmount(),
                        new PriceDetail(quote.pricePerDayAdult(), quote.pricePerDayChild()));
    }

    private Hotel calculateBooking(final BrokerHotel brokerHotel, final long nights, final int adults,
                    final int children) {
        log.info("Calculating booking: hotel {}, {} nights, {} adults, {} children",
                        brokerHotel.name(), nights, adults, children);

        final var rooms = Optional.ofNullable(brokerHotel.rooms()).orElseGet(List::of)
                        .stream()
                        .map(room -> this.calculateRoom(room, nights, adults, children))
                        .toList();

        return new Hotel(brokerHotel.id(), brokerHotel.cityName(), brokerHotel.name(), rooms);
    }

    public Optional<Hotel> getHotelDetails(final Integer codeHotel) {
        log.info("Finding hotel details: {}", codeHotel);
        final var hotelDetails = this.brokerService.getHotelDetails(codeHotel);

        if (hotelDetails == null) {
            return Optional.empty();
        }

        log.info("Hotel found: {}", hotelDetails);
        return Optional.of(this.calculateBooking(hotelDetails, DETAIL_NIGHTS, DETAIL_PAX, DETAIL_PAX));
    }

    public List<Hotel> findHotels(final Integer cityCode, final LocalDate checkin, final LocalDate checkout,
                    final Integer adults, final Integer children) {
        log.info("Finding hotels: city {}, checkin {}, checkout {}", cityCode, checkin, checkout);
        this.validateParty(adults, children);
        final var period = this.calculatePeriod(checkin, checkout);
        final var hotels = Optional.ofNullable(this.brokerService.findHotelsByCity(cityCode)).orElseGet(Set::of);

        log.info("Result of searching hotels: {}", hotels.size());
        return hotels.stream()
                        .map(brokerHotel -> this.calculateBooking(brokerHotel, period, adults, children))
                        .toList();
    }
}

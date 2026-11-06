package br.com.cvc.evaluation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import br.com.cvc.evaluation.broker.dto.BrokerHotelDetail;
import br.com.cvc.evaluation.catalog.HotelDetail;
import br.com.cvc.evaluation.exceptions.RateUnavailableException;
import br.com.cvc.evaluation.catalog.QuotedNight;
import br.com.cvc.evaluation.catalog.BrokerCatalog;
import br.com.cvc.evaluation.catalog.HotelOffer;
import br.com.cvc.evaluation.catalog.QuotedHotel;
import br.com.cvc.evaluation.exceptions.BookingPeriodInvalidException;
import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import br.com.cvc.evaluation.pricing.PricingCalculator;
import br.com.cvc.evaluation.service.provider.BookingSettings;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BrokerCatalog catalog;
    private final BookingSettings settings;

    public BookingService(final BrokerCatalog catalog, final BookingSettings settings) {
        this.catalog = catalog;
        this.settings = settings;
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
        if (nights > this.settings.maxNights()) {
            throw new InvalidRequestException("Period must not exceed " + this.settings.maxNights() + " nights: " + nights);
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



    /**
     * Detalhe do hotel para um período.
     *
     * <p>Preço é por data, então o período é obrigatório: um "preço de referência" de 1 noite
     * fixa seria invenção. Cada regime é cotado; regime sem tarifa publicada volta com
     * {@code available: false} em vez de desaparecer — o hóspede precisa saber que existe e
     * não está à venda.</p>
     */
    public HotelDetail getHotelDetails(final Integer hotelId, final LocalDate checkin, final LocalDate checkout,
                    final Integer adults, final Integer children) {
        log.info("Finding hotel details: {}, {} -> {}, {} adults, {} children", hotelId, checkin, checkout,
                        adults, children);

        this.validateParty(adults, children);
        final var nights = this.calculatePeriod(checkin, checkout);
        final var hotel = this.catalog.hotelDetail(hotelId);

        final var rooms = hotel.roomTypes() == null ? List.<HotelDetail.Room>of()
                        : hotel.roomTypes().stream()
                                        .map(room -> new HotelDetail.Room(room.id(), room.name(), room.maxAdults(),
                                                        room.maxChildren(),
                                                        this.pricedPlans(hotelId, room, checkin, checkout, adults,
                                                                        children, nights)))
                                        .toList();

        return new HotelDetail(hotel.id(), hotel.name(), hotel.cityName(), hotel.dataSource(), rooms);
    }

    private List<HotelDetail.Plan> pricedPlans(final Integer hotelId, final BrokerHotelDetail.Room room,
                    final LocalDate checkin, final LocalDate checkout, final Integer adults, final Integer children,
                    final long nights) {
        if (room.ratePlans() == null) {
            return List.of();
        }

        return room.ratePlans().stream()
                        .map(plan -> this.pricedPlan(hotelId, room.id(), plan, checkin, checkout, adults, children,
                                        nights))
                        .toList();
    }

    private HotelDetail.Plan pricedPlan(final Integer hotelId, final Long roomId, final BrokerHotelDetail.Plan plan,
                    final LocalDate checkin, final LocalDate checkout, final Integer adults, final Integer children,
                    final long nights) {
        try {
            final var quotedNights = this.catalog.quote(hotelId, roomId.intValue(), plan.id().intValue(), checkin,
                            checkout, adults, children, nights);
            final var priced = price(quotedNights, adults, children);

            return new HotelDetail.Plan(plan.id(), plan.name(), plan.board(),
                            plan.cancellationPolicy(), true, this.settings.currency(), priced.totalAmount(),
                            priced.nights());
        } catch (final RateUnavailableException e) {
            return new HotelDetail.Plan(plan.id(), plan.name(), plan.board(),
                            plan.cancellationPolicy(), false, this.settings.currency(), null, List.of());
        }
    }

    /** Noite a noite, com a taxa aplicada — mesma regra da reserva. */
    private PricedNights price(final List<QuotedNight> nights, final int adults, final int children) {
        final var priced = new ArrayList<HotelDetail.Night>(nights.size());
        var total = BigDecimal.ZERO;

        for (final var night : nights) {
            final var adult = PricingCalculator.nightlyRate(night.adult(), this.settings.feeBps());
            final var child = PricingCalculator.nightlyRate(night.child(), this.settings.feeBps());
            priced.add(new HotelDetail.Night(night.date().toString(), adult, child));
            total = total.add(PricingCalculator.nightlyTotal(adult, child, adults, children));
        }

        return new PricedNights(total.setScale(2, RoundingMode.HALF_UP), List.copyOf(priced));
    }

    private record PricedNights(BigDecimal totalAmount, List<HotelDetail.Night> nights) {
    }

    public List<HotelOffer> findHotels(final String cityCode, final LocalDate checkin, final LocalDate checkout,
                    final Integer adults, final Integer children) {
        final var safedCityCode = safe(cityCode);
        log.info("Finding hotels: city {}, checkin {}, checkout {}", safedCityCode, checkin, checkout);
        this.validateParty(adults, children);
        this.calculatePeriod(checkin, checkout);

        final var offers = this.catalog.search(safedCityCode, checkin, checkout, adults, children);
        log.info("Result of searching hotels: {}", offers.size());

        return offers.stream().map(offer -> this.price(offer, adults, children)).toList();
    }

    /**
     * Aplica a taxa por noite — a mesma regra da reserva, para busca e criação mostrarem o
     * mesmo número. Aplicar a taxa sobre o total do período daria outro valor quando a
     * diária tem dízima.
     */
    private static String safe(final String value) {
        // Remove quebras de linha e tabulações antes de logar valor vindo de entrada.
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]", "_");
    }

    private HotelOffer price(final QuotedHotel offer, final int adults, final int children) {
        final var pricedNights = new ArrayList<HotelOffer.PricedNight>(offer.nights().size());
        var total = BigDecimal.ZERO;

        for (final var night : offer.nights()) {
            final var adult = PricingCalculator.nightlyRate(night.adult(), this.settings.feeBps());
            final var child = PricingCalculator.nightlyRate(night.child(), this.settings.feeBps());
            pricedNights.add(new HotelOffer.PricedNight(night.date().toString(), adult, child));
            total = total.add(PricingCalculator.nightlyTotal(adult, child, adults, children));
        }

        return new HotelOffer(offer.hotelId(), offer.hotelName(), offer.cityName(), offer.roomId(),
                        offer.roomTypeName(), offer.ratePlanId(), offer.ratePlanName(), offer.board(),
                        offer.cancellationPolicy(), offer.currency(), offer.dataSource(),
                        total.setScale(2, RoundingMode.HALF_UP), List.copyOf(pricedNights));
    }
}

package br.com.cvc.evaluation.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import br.com.cvc.evaluation.broker.dto.BrokerHotelDetail;
import br.com.cvc.evaluation.catalog.BrokerCatalog;
import br.com.cvc.evaluation.catalog.QuotedHotel;
import br.com.cvc.evaluation.catalog.QuotedNight;
import br.com.cvc.evaluation.exceptions.BookingPeriodInvalidException;
import br.com.cvc.evaluation.exceptions.HotelNotFoundException;
import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import br.com.cvc.evaluation.exceptions.RateUnavailableException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * O serviço orquestra catálogo e cotação (que já vêm do broker) e aplica a taxa por noite.
 * A aritmética fica em {@code PricingCalculator}; aqui se verifica a composição.
 */
@QuarkusTest
class BookingServiceTest {
    private static final BigDecimal NIGHTLY_ADULT = new BigDecimal("900.00");
    private static final BigDecimal NIGHTLY_CHILD = new BigDecimal("450.00");

    @InjectMock
    BrokerCatalog catalog;

    @Inject
    BookingService service;

    @Test
    void hotelDetailPricesEveryRatePlan() {
        final var checkIn = LocalDate.of(2027, 4, 10);
        final var checkOut = checkIn.plusDays(3);
        when(this.catalog.hotelDetail(1)).thenReturn(hotelWithTwoPlans());
        when(this.catalog.quote(eq(1), eq(1), anyInt(), eq(checkIn), eq(checkOut), eq(1), eq(1), eq(3L)))
                        .thenReturn(nights(checkIn, 3));

        final var detail = this.service.getHotelDetails(1, checkIn, checkOut, 1, 1);

        final var plans = detail.rooms().get(0).ratePlans();
        assertAll("hotel-detail",
                        () -> assertEquals(1L, detail.hotelId()),
                        () -> assertEquals("Hotel Serra Verde", detail.hotelName()),
                        () -> assertEquals(2, plans.size()),
                        () -> assertTrue(plans.get(0).available()),
                        () -> assertEquals(0, new BigDecimal("4526.28").compareTo(plans.get(0).totalAmount())),
                        () -> assertEquals(3, plans.get(0).nights().size()),
                        () -> verify(this.catalog).hotelDetail(1));
    }

    @Test
    void ratePlanWithoutRateIsMarkedUnavailable() {
        final var checkIn = LocalDate.of(2027, 4, 10);
        final var checkOut = checkIn.plusDays(3);
        when(this.catalog.hotelDetail(1)).thenReturn(hotelWithTwoPlans());
        when(this.catalog.quote(eq(1), eq(1), anyInt(), any(), any(), anyInt(), anyInt(), anyLong()))
                        .thenThrow(new RateUnavailableException("no rate"));

        final var detail = this.service.getHotelDetails(1, checkIn, checkOut, 1, 1);

        final var plans = detail.rooms().get(0).ratePlans();
        assertAll("unavailable-plan",
                        () -> assertEquals(2, plans.size(), "regime sem tarifa nao some da resposta"),
                        () -> assertFalse(plans.get(0).available()),
                        () -> assertTrue(plans.get(0).nights().isEmpty()));
    }

    @Test
    void unknownHotelPropagatesNotFound() {
        when(this.catalog.hotelDetail(42)).thenThrow(new HotelNotFoundException("Hotel not found: 42"));

        assertThrows(HotelNotFoundException.class,
                        () -> this.service.getHotelDetails(42, LocalDate.now(), LocalDate.now().plusDays(1), 1, 0));
    }

    @Test
    void searchAppliesFeeNightByNight() {
        final var checkIn = LocalDate.of(2027, 4, 10);
        final var checkOut = checkIn.plusDays(3);
        when(this.catalog.search("serra-negra", checkIn, checkOut, 3, 2)).thenReturn(List.of(offer(checkIn, 3)));

        final var offers = this.service.findHotels("serra-negra", checkIn, checkOut, 3, 2);

        final var expectedNight = nightly("900.00").multiply(BigDecimal.valueOf(3))
                        .add(nightly("450.00").multiply(BigDecimal.valueOf(2)));

        assertAll("search",
                        () -> assertEquals(1, offers.size()),
                        () -> assertEquals(expectedNight.multiply(BigDecimal.valueOf(3)), offers.get(0).totalAmount()),
                        () -> assertEquals(nightly("900.00"), offers.get(0).nights().get(0).adult()),
                        () -> assertEquals(3, offers.get(0).nights().size()),
                        () -> verify(this.catalog).search("serra-negra", checkIn, checkOut, 3, 2));
    }

    @Test
    void searchWithoutOffersIsEmpty() {
        final var checkIn = LocalDate.of(2027, 5, 1);
        final var checkOut = checkIn.plusDays(2);
        when(this.catalog.search("serra-negra", checkIn, checkOut, 1, 0)).thenReturn(List.of());

        assertTrue(this.service.findHotels("serra-negra", checkIn, checkOut, 1, 0).isEmpty());
    }

    @Test
    void invertedPeriodIsRejected() {
        final var checkIn = LocalDate.of(2027, 5, 1);

        assertThrows(BookingPeriodInvalidException.class,
                        () -> this.service.findHotels("serra-negra", checkIn, checkIn, 1, 0));
    }

    @Test
    void tooLongPeriodIsRejected() {
        final var checkIn = LocalDate.of(2027, 5, 1);

        assertThrows(InvalidRequestException.class,
                        () -> this.service.findHotels("serra-negra", checkIn, checkIn.plusDays(60), 1, 0));
    }

    @Test
    void invalidPartyIsRejected() {
        final var checkIn = LocalDate.of(2027, 5, 1);

        assertAll("party",
                        () -> assertThrows(InvalidRequestException.class,
                                        () -> this.service.findHotels("serra-negra", checkIn,
                                                        checkIn.plusDays(1), 0, 0)),
                        () -> assertThrows(InvalidRequestException.class,
                                        () -> this.service.findHotels("serra-negra", checkIn,
                                                        checkIn.plusDays(1), 1, -1)));
    }

    private static BrokerHotelDetail hotelWithTwoPlans() {
        return new BrokerHotelDetail(1L, "Hotel Serra Verde", "serra-negra", "Serra Negra", null, null, "SEED",
                        List.of(new BrokerHotelDetail.Room(1L, "Standard Casal", 2, 2,
                                        List.of(new BrokerHotelDetail.Plan(1L, "Café — flexível", "BB", "FLEXIBLE"),
                                                        new BrokerHotelDetail.Plan(2L, "Somente quarto", "RO",
                                                                        "NON_REFUNDABLE")))));
    }

    private static List<QuotedNight> nights(final LocalDate checkIn, final int count) {
        return IntStream.range(0, count)
                        .mapToObj(offset -> new QuotedNight(checkIn.plusDays(offset), NIGHTLY_ADULT, NIGHTLY_CHILD))
                        .toList();
    }

    private static QuotedHotel offer(final LocalDate checkIn, final int count) {
        final var quotedNights = IntStream.range(0, count)
                        .mapToObj(offset -> new QuotedHotel.Night(checkIn.plusDays(offset), NIGHTLY_ADULT,
                                        NIGHTLY_CHILD))
                        .toList();

        return new QuotedHotel(1, "Hotel Serra Verde", "Serra Negra", 1, "Standard Casal", 1, "Café", "BB",
                        "FLEXIBLE", "BRL", "SEED", quotedNights);
    }

    private static BigDecimal nightly(final String baseRate) {
        return new BigDecimal(baseRate)
                        .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(1176).movePointLeft(4)))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}

package br.com.cvc.evaluation.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import br.com.cvc.evaluation.broker.BrokerService;
import br.com.cvc.evaluation.catalog.BrokerCatalog;
import br.com.cvc.evaluation.catalog.QuotedHotel;
import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import br.com.cvc.evaluation.broker.dto.BrokerHotelRoom;
import br.com.cvc.evaluation.broker.dto.Price;
import br.com.cvc.evaluation.domain.Hotel;
import br.com.cvc.evaluation.domain.Room;
import br.com.cvc.evaluation.exceptions.BookingPeriodInvalidException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

/**
 * A precificação agora é pura ({@link br.com.cvc.evaluation.pricing.PricingCalculator}),
 * então este teste não precisa mais mockar a taxa: ele usa a configuração real do perfil
 * de teste ({@code booking.fee-bps: 500}) e compara com o valor esperado calculado aqui.
 */
@QuarkusTest
class BookingServiceTest {
    private static final int FEE_BPS = 500; // 5,00% — precisa bater com application.yaml (test)
    private static final BigDecimal FEE_FACTOR =
                    BigDecimal.ONE.add(BigDecimal.valueOf(FEE_BPS).movePointLeft(4));

    @InjectMock
    BrokerCatalog catalog;

    @Inject
    BookingService bookingService;

    @InjectMock
    @RestClient
    BrokerService brokerService;

    @Test
    void testGetHotelDetails() {
        // Arranges
        final var brokerHotel = hotel(1, "Bacana Hotel", "Gotham City", room(1, "Luxury", "900.00", "450.00"));
        when(brokerService.getHotelDetails(anyInt())).thenReturn(brokerHotel);

        // Act
        final var hotel = bookingService.getHotelDetails(1).orElseThrow();

        // Asserts
        final var room = hotel.rooms().get(0);
        assertAll("hotel-details",
                        () -> assertThat(hotel.id(), is(1)),
                        () -> assertThat(hotel.name(), is("Bacana Hotel")),
                        () -> assertFalse(hotel.rooms().isEmpty()),
                        () -> assertEquals(nightly("900.00"), room.priceDetail().pricePerDayAdult()),
                        () -> assertEquals(nightly("450.00"), room.priceDetail().pricePerDayChild()),
                        // O detalhe é 1 adulto + 1 criança em 1 noite: o total precisa fechar com ele.
                        () -> assertEquals(room.priceDetail().pricePerDayAdult()
                                        .add(room.priceDetail().pricePerDayChild()), room.totalPrice()),
                        () -> verify(brokerService).getHotelDetails(anyInt()));
    }

    @Test
    void testGetNoHotelDetails() {
        // Arranges
        when(brokerService.getHotelDetails(anyInt())).thenReturn(null);

        // Act
        final var hotelDetails = bookingService.getHotelDetails(1);

        // Assert
        assertTrue(hotelDetails.isEmpty());
    }

    @Test
    void testFindHotels() {
        // A busca vem da ACL: o teste mocka o broker, nao o cliente REST legado.
        final var checkin = LocalDate.of(2027, 4, 10);
        final var checkout = checkin.plusDays(3);
        when(catalog.search("serra-negra", checkin, checkout, 3, 2)).thenReturn(List.of(
                        quoted(10, "Bacana Hotel", 1, "900.00", "450.00", checkin, 3),
                        quoted(11, "Wayne Hotel", 2, "500.00", "250.00", checkin, 3)));

        final var offers = bookingService.findHotels("serra-negra", checkin, checkout, 3, 2);

        final var luxury = offers.stream().filter(o -> o.hotelId() == 10).findFirst().orElseThrow();
        final var expectedNight = nightly("900.00").multiply(BigDecimal.valueOf(3))
                        .add(nightly("450.00").multiply(BigDecimal.valueOf(2)));

        assertAll("find-hotels",
                        () -> assertThat(offers.size(), is(2)),
                        () -> assertEquals(expectedNight.multiply(BigDecimal.valueOf(3))
                                        .setScale(2, RoundingMode.HALF_UP), luxury.totalAmount()),
                        () -> assertEquals(nightly("900.00"), luxury.nights().get(0).adult()),
                        () -> assertEquals(3, luxury.nights().size()),
                        () -> verify(catalog).search("serra-negra", checkin, checkout, 3, 2));
    }

    @Test
    void testFindNoHotels() {
        final var checkin = LocalDate.of(2027, 5, 1);
        final var checkout = checkin.plusDays(2);
        when(catalog.search("serra-negra", checkin, checkout, 1, 0)).thenReturn(List.of());

        assertTrue(bookingService.findHotels("serra-negra", checkin, checkout, 1, 0).isEmpty());
    }

    @Test
    void testFindHotelsWithInvalidPeriod() {
        // Arranges
        final var checkin = LocalDate.now();
        final var checkout = checkin.minusDays(3);

        // Act | Assert
        assertThrows(BookingPeriodInvalidException.class,
                        () -> bookingService.findHotels("serra-negra", checkin, checkout, 1, 0));
    }

    @Test
    void testFindHotelsWithZeroNights() {
        // Checkout igual ao checkin significaria zero noites e preço zero.
        final var checkin = LocalDate.now();

        assertThrows(BookingPeriodInvalidException.class,
                        () -> bookingService.findHotels("serra-negra", checkin, checkin, 1, 0));
    }

    private static Room roomOf(final List<Hotel> hotels, final int hotelId) {
        return hotels.stream()
                        .filter(hotel -> hotel.id().equals(hotelId))
                        .findFirst()
                        .orElseThrow()
                        .rooms()
                        .get(0);
    }

    private static QuotedHotel quoted(final int hotelId, final String name, final int roomId, final String adult,
                    final String child, final LocalDate checkIn, final int nights) {
        final var quotedNights = java.util.stream.IntStream.range(0, nights)
                        .mapToObj(offset -> new QuotedHotel.Night(checkIn.plusDays(offset),
                                        new BigDecimal(adult), new BigDecimal(child)))
                        .toList();

        return new QuotedHotel(hotelId, name, "Serra Negra", roomId, "Standard", 1, "Café", "BB", "FLEXIBLE",
                        "BRL", quotedNights);
    }

    private static BigDecimal nightly(final String baseRate) {
        return new BigDecimal(baseRate).multiply(FEE_FACTOR).setScale(2, RoundingMode.HALF_UP);
    }

    private static BrokerHotel hotel(final int id, final String name, final String cityName,
                    final BrokerHotelRoom... rooms) {
        return new BrokerHotel(id, name, 55, cityName, List.of(rooms));
    }

    private static BrokerHotelRoom room(final int id, final String category, final String adultPrice,
                    final String childPrice) {
        return new BrokerHotelRoom(id, category, new Price(new BigDecimal(adultPrice), new BigDecimal(childPrice)));
    }
}

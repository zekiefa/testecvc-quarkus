package br.com.cvc.evaluation.service;

import static br.com.cvc.evaluation.fixtures.FixtureUtil.nextBrokerHotel;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import br.com.cvc.evaluation.broker.BrokerService;
import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import br.com.cvc.evaluation.domain.Hotel;
import br.com.cvc.evaluation.exceptions.BookingPeriodInvalidException;
import br.com.cvc.evaluation.exceptions.HotelNotFoundException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BookingServiceTest {
    @Inject
    BookingService bookingService;

    @InjectMock
    FeeService feeService;

    @InjectMock
    @RestClient
    BrokerService brokerService;

    @Test
    void testGetHotelDetails() {
        // Arranges
        final var brokerHotel = nextBrokerHotel();
        final var fee = BigDecimal.ONE;
        when(brokerService.getHotelDetails(anyInt())).thenReturn(brokerHotel);
        when(feeService.calculateFee(any(), anyLong())).thenReturn(fee);

        // Act
        final var hotel = bookingService.getHotelDetails(1).orElse(this.emptyHotel());

        // Asserts
        assertAll("hotel-details",
                        () -> assertThat(hotel.id(), is(brokerHotel.id())),
                        () -> assertThat(hotel.cityName(), is(brokerHotel.cityName())),
                        () -> assertFalse(hotel.rooms().isEmpty()),
                        () -> verify(brokerService).getHotelDetails(anyInt()),
                        () -> verify(feeService, times(brokerHotel.rooms().size() * 2))
                                        .calculateFee(any(), anyLong())
        );
    }

    @Test
    void testGetNoHotelDetails() {
        // Arranges
        when(brokerService.getHotelDetails(anyInt())).thenReturn(null);

        // Act | Assert
        assertThrows(HotelNotFoundException.class,
                        () -> bookingService.getHotelDetails(1));
    }

    @Test
    void testFindHotels() {
        // Arranges
        final var brokerHotels = List.of(nextBrokerHotel(), nextBrokerHotel());
        final var fee = BigDecimal.ONE;
        when(brokerService.findHotelsByCity(anyInt())).thenReturn(Set.copyOf(brokerHotels));
        when(feeService.calculateFee(any(), anyLong())).thenReturn(fee);

        // Act
        final var checkin = LocalDate.now();
        final var checkout = checkin.plusDays(DayOfWeek.values().length);
        final var hotels = bookingService.findHotels(27, checkin, checkout, 3, 2);

        // Asserts
        assertAll("find-hotels.json",
                        () -> assertFalse(hotels.isEmpty()),
                        () -> assertThat(hotels.size(), is(2)),
                        () -> verify(brokerService).findHotelsByCity(anyInt()),
                        () -> verify(feeService, times(brokerHotels.size() *
                                        brokerHotels.stream().map(BrokerHotel::rooms).mapToInt(List::size).sum()))
                                        .calculateFee(any(), anyLong())
        );
    }

    @Test
    void testFindNoHotels() {
        // Arranges
        final var fee = BigDecimal.ONE;
        when(brokerService.findHotelsByCity(anyInt())).thenReturn(Collections.emptySet());
        when(feeService.calculateFee(any(), anyLong())).thenReturn(fee);

        // Act
        final var checkin = LocalDate.now();
        final var checkout = checkin.plusDays(DayOfWeek.values().length);
        final var hotels = bookingService.findHotels(27, checkin, checkout, 3, 2);

        // Asserts
        assertTrue(hotels.isEmpty());
    }

    @Test
    void testFindHotelsWithInvalidPeriod() {
        // Arranges
        final var checkin = LocalDate.now();
        final var checkout = checkin.minusDays(DayOfWeek.values().length);

        // Act | Assert
        assertThrows(BookingPeriodInvalidException.class,
                        () -> bookingService.findHotels(27, checkin, checkout, 3, 2));
    }

    private Hotel emptyHotel() {
        return new Hotel(1, "", "", Collections.emptyList());
    }
}
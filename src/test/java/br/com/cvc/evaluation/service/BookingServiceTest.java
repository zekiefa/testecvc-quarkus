package br.com.cvc.evaluation.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import br.com.cvc.evaluation.broker.BrokerService;
import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import br.com.cvc.evaluation.domain.Hotel;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jeasy.random.EasyRandom;
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

    private final EasyRandom easyRandom = new EasyRandom();

    @Test
    void testGetHotelDetails() {
        // Arranges
        final var brokerHotel = easyRandom.nextObject(BrokerHotel.class);
        final var fee = BigDecimal.ONE;
        when(brokerService.getHotelDetails(anyInt())).thenReturn(brokerHotel);
        when(feeService.calculateFee(any(), anyLong())).thenReturn(fee);

        // Act
        final var hotel = bookingService.getHotelDetails(1).orElse(Hotel.builder().build());

        // Asserts
        assertAll("hotel-details",
                        () -> assertThat(hotel.getId(), is(brokerHotel.getId())),
                        () -> assertThat(hotel.getCityName(), is(brokerHotel.getCityName())),
                        () -> assertFalse(hotel.getRooms().isEmpty()),
                        () -> verify(brokerService).getHotelDetails(anyInt()),
                        () -> verify(feeService, times(brokerHotel.getRooms().size() * 2))
                                        .calculateFee(any(), anyLong())
        );
    }

    @Test
    void testFindHotels() {
        // Arranges
        final var brokerHotels = easyRandom.objects(BrokerHotel.class, 2)
                        .collect(Collectors.toList());
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
                                        brokerHotels.stream().map(BrokerHotel::getRooms).mapToInt(List::size).sum()))
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
    void testGetEmptyHotelDetails() {
        // Arranges
        final var fee = BigDecimal.ONE;
        when(brokerService.getHotelDetails(anyInt())).thenReturn(null);
        when(feeService.calculateFee(any(), anyLong())).thenReturn(fee);

        // Act
        final var hotel = bookingService.getHotelDetails(1);

        // Asserts
        assertTrue(hotel.isEmpty());
    }
}
package br.com.cvc.evaluation.broker;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.inject.Inject;

import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
public class BrokerServiceTest {
    @Inject
    @RestClient
    BrokerService brokerService;
    @Test
    void testGetHotelDetailsEndpoint() {
        final var idHotel = 1;

        final var hotelDetails = this.brokerService.getHotelDetails(idHotel);

        assertAll("success",
            () -> assertNotNull(hotelDetails),
            () -> assertThat(hotelDetails.id(), is(idHotel))
        );
    }
    @Test
    void testFindHotelsByCityEndpoint() {
        final var codeCity = 55;

        final var hotelsByCity = this.brokerService.findHotelsByCity(codeCity);

        assertAll("success",
                        () -> assertThat(hotelsByCity, not(emptyCollectionOf(BrokerHotel.class))),
                        () -> assertThat(hotelsByCity.size(), is(2)),
                        () -> assertTrue(hotelsByCity.stream()
                                        .allMatch(brokerHotel -> brokerHotel.cityCode().equals(codeCity)))
        );
    }

}

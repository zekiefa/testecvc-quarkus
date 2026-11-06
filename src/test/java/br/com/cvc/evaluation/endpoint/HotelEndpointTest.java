package br.com.cvc.evaluation.endpoint;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.cvc.evaluation.catalog.HotelDetail;
import br.com.cvc.evaluation.extensions.TokenBuilder;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/** Detalhe do hotel: período obrigatório e cada regime cotado. */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class HotelEndpointTest {
    private final TokenBuilder tokenBuilder = new TokenBuilder();

    @Test
    void pricesEachRatePlanForThePeriod() {
        final var checkIn = LocalDate.now().plusDays(10);
        final var checkOut = checkIn.plusDays(3);

        final var detail = given().headers("Authorization", "Bearer " + bearer())
                        .when().get("/api/v1/hotels/1?checkIn=" + checkIn + "&checkOut=" + checkOut
                                        + "&adults=1&children=1")
                        .then().statusCode(HttpStatus.SC_OK)
                        .extract().as(HotelDetail.class);

        assertEquals(1, detail.hotelId());
        assertEquals("Hotel Serra Verde", detail.hotelName());
        assertEquals(1, detail.rooms().size(), "um quarto no stub");

        assertEquals("SEED", detail.dataSource(), "origem do dado sobrevive ao salto");
        final var plans = detail.rooms().getFirst().ratePlans();
        assertEquals(2, plans.size(), "dois regimes cotados");
        for (final var plan : plans) {
            assertTrue(plan.available(), "regime com tarifa publicada");
            assertEquals(0, new BigDecimal("4526.28").compareTo(plan.totalAmount()),
                            "900 + 450 por noite, com 5%, 3 noites");
            assertEquals(3, plan.nights().size());
        }
    }

    @Test
    void periodIsRequired() {
        given().headers("Authorization", "Bearer " + bearer())
                        .when().get("/api/v1/hotels/1")
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST)
                        .body("type", containsString("/invalid-request"));
    }

    @Test
    void unknownHotelIsNotFound() {
        final var checkIn = LocalDate.now().plusDays(10);

        given().headers("Authorization", "Bearer " + bearer())
                        .when().get("/api/v1/hotels/42?checkIn=" + checkIn + "&checkOut=" + checkIn.plusDays(2))
                        .then().statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void requiresToken() {
        final var checkIn = LocalDate.now().plusDays(10);

        given().when().get("/api/v1/hotels/1?checkIn=" + checkIn + "&checkOut=" + checkIn.plusDays(2))
                        .then().statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    private String bearer() {
        return this.tokenBuilder.createJWT("usuario", 86400000L);
    }
}

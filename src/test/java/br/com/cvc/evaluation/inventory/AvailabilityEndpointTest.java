package br.com.cvc.evaluation.inventory;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.time.LocalDate;

import br.com.cvc.evaluation.booking.Booking;
import br.com.cvc.evaluation.extensions.TokenBuilder;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Disponibilidade = capacidade − reservado, por noite. Sem linha de inventário, vale a
 * capacidade padrão (premissa do ADR-002: 1 quarto por noite).
 */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class AvailabilityEndpointTest {
    private static final String URL = "/api/v1/hotels/1/availability";
    private static final LocalDate CHECK_IN = LocalDate.of(2027, 2, 10);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(3);

    private final TokenBuilder tokenBuilder = new TokenBuilder();

    @Inject
    InventoryService inventory;

    @Inject
    InventoryRepository repository;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> Booking.deleteAll());
        QuarkusTransaction.requiringNew().run(() -> this.inventory.materialize(1, 1, CHECK_IN, CHECK_OUT));
    }

    @Test
    void reportsTheDefaultCapacityWhenNothingIsReserved() {
        given()
                        .header("Authorization", bearer())
                        .when().get(query(CHECK_IN, CHECK_OUT))
                        .then().statusCode(HttpStatus.SC_OK)
                        .body("rooms[0].roomId", is(1))
                        .body("rooms[0].availableRooms", is(1))
                        .body("rooms[0].nights.size()", is(3));
    }

    @Test
    void reportsZeroOnTheNightAlreadySold() {
        QuarkusTransaction.requiringNew().run(() -> this.repository.hold(1, 1, CHECK_IN));

        given()
                        .header("Authorization", bearer())
                        .when().get(query(CHECK_IN, CHECK_OUT))
                        .then().statusCode(HttpStatus.SC_OK)
                        .body("rooms[0].nights[0].available", is(0))
                        // o mínimo entre as noites é o que trava o período
                        .body("rooms[0].availableRooms", is(0));
    }

    @Test
    void rejectsInvertedPeriod() {
        given().header("Authorization", bearer())
                        .when().get(query(CHECK_OUT, CHECK_IN))
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void rejectsMalformedDate() {
        given().header("Authorization", bearer())
                        .when().get("/api/v1/hotels/1/availability?checkIn=10/02/2027&checkOut=2027-02-13")
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void returnsNotFoundForUnknownHotel() {
        given().header("Authorization", bearer())
                        .when().get("/api/v1/hotels/42/availability?checkIn=" + CHECK_IN + "&checkOut=" + CHECK_OUT)
                        .then().statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void returnsServiceUnavailableWhenBrokerFails() {
        given().header("Authorization", bearer())
                        .when().get("/api/v1/hotels/999/availability?checkIn=" + CHECK_IN + "&checkOut=" + CHECK_OUT)
                        .then().statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
    }

    @Test
    void requiresAuthentication() {
        given().when().get(query(CHECK_IN, CHECK_OUT))
                        .then().statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    private String query(final LocalDate checkIn, final LocalDate checkOut) {
        return URL + "?checkIn=" + checkIn + "&checkOut=" + checkOut;
    }

    private String bearer() {
        return "Bearer " + this.tokenBuilder.createJWT("usuario", 86400000L);
    }
}

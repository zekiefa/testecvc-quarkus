package br.com.cvc.evaluation.exceptions;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.time.LocalDate;

import br.com.cvc.evaluation.booking.Booking;
import br.com.cvc.evaluation.booking.BookingRepository;
import br.com.cvc.evaluation.booking.dto.BookingResponse;
import br.com.cvc.evaluation.booking.dto.CreateBookingRequest;
import br.com.cvc.evaluation.extensions.TokenBuilder;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import br.com.cvc.evaluation.inventory.InventoryRepository;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Todo erro da API sai como {@code application/problem+json} (RFC 7807), com um
 * {@code type} estável por classe de problema e um {@code detail} que diz o que corrigir.
 */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class ProblemDetailsTest {
    private static final String BOOKINGS = "/api/v1/bookings";
    private static final LocalDate CHECK_IN = LocalDate.of(2027, 4, 10);

    private final TokenBuilder tokenBuilder = new TokenBuilder();

    @Inject
    BookingRepository repository;

    @Inject
    InventoryRepository inventory;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            Booking.deleteAll();
            this.inventory.deleteAll();
        });
    }

    @Test
    void domainErrorCarriesAStableType() {
        given().header("Authorization", bearer())
                        .header("Idempotency-Key", "problem-period")
                        .contentType(ContentType.JSON)
                        .body(new CreateBookingRequest(1, 1, 1, CHECK_IN, CHECK_IN, 1, 1))
                        .when().post(BOOKINGS)
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST)
                        .header("Content-Type", containsString("application/problem+json"))
                        .body("status", is(400))
                        .body("title", is("Bad Request"))
                        .body("type", containsString("/booking-period-invalid"))
                        .body("detail", containsString("Checkout must be after checkin"));
    }

    @Test
    void missingHeaderIsAProblemToo() {
        given().header("Authorization", bearer())
                        .contentType(ContentType.JSON)
                        .body(new CreateBookingRequest(1, 1, 1, CHECK_IN, CHECK_IN.plusDays(2), 1, 1))
                        .when().post(BOOKINGS)
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST)
                        .header("Content-Type", containsString("application/problem+json"))
                        .body("type", containsString("/invalid-request"))
                        .body("detail", containsString("Idempotency-Key"));
    }

    @Test
    void validationFailureNamesTheField() {
        given().header("Authorization", bearer())
                        .header("Idempotency-Key", "problem-validation")
                        .contentType(ContentType.JSON)
                        .body("""
                                        {"roomId": 1, "checkIn": "2027-04-10", "checkOut": "2027-04-12",
                                         "adults": 1, "children": 0}
                                        """)
                        .when().post(BOOKINGS)
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST)
                        .header("Content-Type", containsString("application/problem+json"))
                        .body("type", containsString("/validation-failed"))
                        .body("detail", containsString("hotelId"));
    }

    @Test
    void unknownBookingIsProblemJson() {
        given().header("Authorization", bearer())
                        .when().get(BOOKINGS + "/" + java.util.UUID.randomUUID())
                        .then().statusCode(HttpStatus.SC_NOT_FOUND)
                        .header("Content-Type", containsString("application/problem+json"))
                        .body("status", is(404))
                        // exceção de domínio: type próprio e estável, não about:blank
                        .body("type", containsString("/booking-not-found"));
    }

    @Test
    void invalidTransitionCarriesItsOwnType() {
        final var booking = create();

        given().header("Authorization", bearer())
                        .when().post(BOOKINGS + "/" + booking.id() + "/cancel")
                        .then().statusCode(HttpStatus.SC_OK);

        given().header("Authorization", bearer())
                        .when().post(BOOKINGS + "/" + booking.id() + "/cancel")
                        .then().statusCode(HttpStatus.SC_CONFLICT)
                        .header("Content-Type", containsString("application/problem+json"))
                        .body("type", containsString("/invalid-booking-state"));
    }

    @Test
    void unknownRouteIsProblemJson() {
        given().when().get("/api/v1/does-not-exist")
                        .then().statusCode(HttpStatus.SC_NOT_FOUND)
                        .header("Content-Type", containsString("application/problem+json"))
                        .body("status", is(404));
    }

    @Test
    void brokerFailureIsProblemJson() {
        given().header("Authorization", bearer())
                        .header("Idempotency-Key", "problem-broker")
                        .contentType(ContentType.JSON)
                        .body(new CreateBookingRequest(999, 1, 1, CHECK_IN, CHECK_IN.plusDays(2), 1, 1))
                        .when().post(BOOKINGS)
                        .then().statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE)
                        .header("Content-Type", containsString("application/problem+json"))
                        .body("type", containsString("/broker-unavailable"));
    }

    private BookingResponse create() {
        return given().header("Authorization", bearer())
                        .header("Idempotency-Key", "problem-create")
                        .contentType(ContentType.JSON)
                        .body(new CreateBookingRequest(1, 1, 1, CHECK_IN, CHECK_IN.plusDays(2), 1, 1))
                        .when().post(BOOKINGS)
                        .then().statusCode(HttpStatus.SC_CREATED)
                        .extract().as(BookingResponse.class);
    }

    private String bearer() {
        return "Bearer " + this.tokenBuilder.createJWT("usuario", 86400000L);
    }
}

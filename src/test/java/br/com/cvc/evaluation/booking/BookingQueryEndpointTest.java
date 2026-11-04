package br.com.cvc.evaluation.booking;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.UUID;

import br.com.cvc.evaluation.booking.dto.BookingResponse;
import br.com.cvc.evaluation.booking.dto.CreateBookingRequest;
import br.com.cvc.evaluation.extensions.TokenBuilder;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import br.com.cvc.evaluation.inventory.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * GET e cancelamento com autorização por dono: quem não criou a reserva recebe 404
 * (nunca 403, que confirmaria a existência do id).
 */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class BookingQueryEndpointTest {
    private static final String BOOKINGS = "/api/v1/bookings";
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 1);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

    private final TokenBuilder tokenBuilder = new TokenBuilder();

    @Inject
    BookingRepository repository;

    @Inject
    InventoryRepository inventory;

    @BeforeEach
    @Transactional
    void cleanUp() {
        Booking.deleteAll();
        this.inventory.deleteAll();
    }

    @Test
    void returnsTheBookingToItsOwner() {
        final var created = create("usuario", "query-get-1");

        final var fetched = given()
                        .header("Authorization", bearer("usuario"))
                        .when().get(BOOKINGS + "/" + created.id())
                        .then().statusCode(HttpStatus.SC_OK)
                        .extract().as(BookingResponse.class);

        assertAll("get",
                        () -> assertEquals(created.id(), fetched.id()),
                        () -> assertEquals("CONFIRMED", fetched.status()));
    }

    @Test
    void hidesSomeoneElsesBooking() {
        final var created = create("usuario", "query-other-1");

        given()
                        .header("Authorization", bearer("outro"))
                        .when().get(BOOKINGS + "/" + created.id())
                        .then().statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void returnsNotFoundForUnknownId() {
        given()
                        .header("Authorization", bearer("usuario"))
                        .when().get(BOOKINGS + "/" + UUID.randomUUID())
                        .then().statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void cancelsBookingOfTheOwner() {
        final var created = create("usuario", "query-cancel-1");

        final var cancelled = given()
                        .header("Authorization", bearer("usuario"))
                        .when().post(BOOKINGS + "/" + created.id() + "/cancel")
                        .then().statusCode(HttpStatus.SC_OK)
                        .extract().as(BookingResponse.class);

        assertEquals("CANCELLED", cancelled.status());
    }

    @Test
    void rejectsSecondCancelAsConflict() {
        final var created = create("usuario", "query-cancel-2");

        given().header("Authorization", bearer("usuario"))
                        .when().post(BOOKINGS + "/" + created.id() + "/cancel")
                        .then().statusCode(HttpStatus.SC_OK);

        given().header("Authorization", bearer("usuario"))
                        .when().post(BOOKINGS + "/" + created.id() + "/cancel")
                        .then().statusCode(HttpStatus.SC_CONFLICT);
    }

    @Test
    void doesNotCancelSomeoneElsesBooking() {
        final var created = create("usuario", "query-cancel-3");

        given().header("Authorization", bearer("outro"))
                        .when().post(BOOKINGS + "/" + created.id() + "/cancel")
                        .then().statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void requiresAuthentication() {
        final var created = create("usuario", "query-auth-1");

        given().when().get(BOOKINGS + "/" + created.id())
                        .then().statusCode(HttpStatus.SC_UNAUTHORIZED);
        given().when().post(BOOKINGS + "/" + created.id() + "/cancel")
                        .then().statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    private BookingResponse create(final String subject, final String key) {
        return given()
                        .header("Authorization", bearer(subject))
                        .header("Idempotency-Key", key)
                        .contentType(ContentType.JSON)
                        .body(new CreateBookingRequest(1, 1, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .when().post(BOOKINGS)
                        .then().statusCode(HttpStatus.SC_CREATED)
                        .extract().as(BookingResponse.class);
    }

    private String bearer(final String subject) {
        return "Bearer " + this.tokenBuilder.createJWT(subject, 86400000L);
    }
}

package br.com.cvc.evaluation.booking;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
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
 * POST /api/v1/bookings: criação, replay idempotente, autorização por dono e
 * tratamento de falha do broker.
 *
 * <p>Hotel 1 / quarto 1 tem tarifa 900 (adulto) e 450 (criança). Com a taxa de 700 bps
 * e 1 adulto + 1 criança, a diária fica 963.00 + 481.50.</p>
 */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class BookingCreationEndpointTest {
    private static final String BOOKINGS = "/api/v1/bookings";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 10);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(3);

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
    void createsBookingWithFrozenPrice() {
        final var response = given()
                        .header("Authorization", bearer("usuario"))
                        .header(IDEMPOTENCY_HEADER, "key-create-1")
                        .contentType(ContentType.JSON)
                        .body(request(1, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .when().post(BOOKINGS)
                        .then()
                        .statusCode(HttpStatus.SC_CREATED)
                        .header("Location", containsString("/api/v1/bookings/"))
                        .extract().as(BookingResponse.class);

        assertAll("created",
                        () -> assertEquals("CONFIRMED", response.status()),
                        () -> assertEquals("BRL", response.currency()),
                        () -> assertEquals(3, response.nightlyBreakdown().size(), "3 noites"),
                        () -> assertEquals(0, new BigDecimal("963.00")
                                        .compareTo(response.nightlyBreakdown().get(0).adultAmount())),
                        () -> assertEquals(0, new BigDecimal("4333.50").compareTo(response.totalAmount()),
                                        "(963.00 + 481.50) * 3"));
    }

    @Test
    @Transactional
    void replaysSameKeyWithoutDuplicating() {
        final var first = post("usuario", "key-replay-1", request(1, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract().as(BookingResponse.class);

        final var second = post("usuario", "key-replay-1", request(1, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .statusCode(HttpStatus.SC_OK)
                        .extract().as(BookingResponse.class);

        assertAll("replay",
                        () -> assertEquals(first.id(), second.id(), "mesma reserva"),
                        () -> assertEquals(1, repository.count(), "uma única reserva persistida"));
    }

    @Test
    void rejectsSameKeyFromAnotherUser() {
        post("usuario", "key-conflict-1", request(1, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .statusCode(HttpStatus.SC_CREATED);

        post("outro", "key-conflict-1", request(1, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .statusCode(HttpStatus.SC_CONFLICT);
    }

    @Test
    void rejectsMissingIdempotencyKey() {
        given()
                        .header("Authorization", bearer("usuario"))
                        .contentType(ContentType.JSON)
                        .body(request(1, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .when().post(BOOKINGS)
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void rejectsZeroNights() {
        post("usuario", "key-zero-nights", request(1, 1, CHECK_IN, CHECK_IN, 1, 1))
                        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void rejectsWithoutToken() {
        given()
                        .header(IDEMPOTENCY_HEADER, "key-no-token")
                        .contentType(ContentType.JSON)
                        .body(request(1, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .when().post(BOOKINGS)
                        .then().statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    void returnsNotFoundForUnknownHotel() {
        post("usuario", "key-unknown-hotel", request(42, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void returnsServiceUnavailableWhenBrokerFails() {
        post("usuario", "key-broker-down", request(999, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectsUnknownRoomInKnownHotel() {
        post("usuario", "key-unknown-room", request(1, 77, CHECK_IN, CHECK_OUT, 1, 1))
                        .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void keepsTheOwnerFromTheToken() {
        final var created = post("usuario", "key-owner", request(1, 1, CHECK_IN, CHECK_OUT, 1, 1))
                        .statusCode(HttpStatus.SC_CREATED)
                        .extract().as(BookingResponse.class);

        final var stored = repository.findBookingById(created.id()).orElseThrow();
        assertThat(stored.userSubject, is("usuario"));
        assertThat(stored.id, is(UUID.fromString(created.id().toString())));
    }

    private io.restassured.response.ValidatableResponse post(final String subject, final String key,
                    final CreateBookingRequest body) {
        return given()
                        .header("Authorization", bearer(subject))
                        .header(IDEMPOTENCY_HEADER, key)
                        .contentType(ContentType.JSON)
                        .body(body)
                        .when().post(BOOKINGS)
                        .then();
    }

    private String bearer(final String subject) {
        return "Bearer " + this.tokenBuilder.createJWT(subject, 86400000L);
    }

    private static CreateBookingRequest request(final int hotelId, final int roomId, final LocalDate checkIn,
                    final LocalDate checkOut, final int adults, final int children) {
        return new CreateBookingRequest(hotelId, roomId, checkIn, checkOut, adults, children);
    }
}

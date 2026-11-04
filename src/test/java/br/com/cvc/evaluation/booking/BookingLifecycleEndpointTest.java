package br.com.cvc.evaluation.booking;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

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
 * Check-in, check-out e no-show: as regras de calendário em volta do grafo de estados.
 * As datas são relativas a hoje para o teste não apodrecer.
 */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class BookingLifecycleEndpointTest {
    private static final String BOOKINGS = "/api/v1/bookings";
    private static final LocalDate TODAY = LocalDate.now();

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
    void checksInOnTheCheckInDate() {
        final var booking = create("lifecycle-in-ok", TODAY, TODAY.plusDays(2));

        final var result = post(booking.id(), "/check-in", HttpStatus.SC_OK).as(BookingResponse.class);

        assertEquals("CHECKED_IN", result.status());
    }

    @Test
    void refusesCheckInBeforeTheDate() {
        final var booking = create("lifecycle-in-early", TODAY.plusDays(2), TODAY.plusDays(4));

        post(booking.id(), "/check-in", HttpStatus.SC_CONFLICT);
    }

    @Test
    void refusesCheckInAfterThePeriodEnded() {
        final var booking = create("lifecycle-in-late", TODAY.minusDays(5), TODAY.minusDays(2));

        post(booking.id(), "/check-in", HttpStatus.SC_CONFLICT);
    }

    @Test
    void checksOutAfterCheckIn() {
        final var booking = create("lifecycle-out-ok", TODAY, TODAY.plusDays(2));
        post(booking.id(), "/check-in", HttpStatus.SC_OK);

        final var result = post(booking.id(), "/check-out", HttpStatus.SC_OK).as(BookingResponse.class);

        assertEquals("CHECKED_OUT", result.status());
    }

    @Test
    void refusesCheckOutWithoutCheckIn() {
        final var booking = create("lifecycle-out-early", TODAY, TODAY.plusDays(2));

        post(booking.id(), "/check-out", HttpStatus.SC_CONFLICT);
    }

    @Test
    void recordsNoShowOnlyAfterTheCheckInDate() {
        final var booking = create("lifecycle-noshow-ok", TODAY.minusDays(3), TODAY.minusDays(1));

        final var result = post(booking.id(), "/no-show", HttpStatus.SC_OK).as(BookingResponse.class);

        assertEquals("NO_SHOW", result.status());
    }

    @Test
    void refusesNoShowOnTheCheckInDate() {
        final var booking = create("lifecycle-noshow-early", TODAY, TODAY.plusDays(2));

        post(booking.id(), "/no-show", HttpStatus.SC_CONFLICT);
    }

    @Test
    void hidesSomeoneElsesBooking() {
        final var booking = create("lifecycle-owner", TODAY, TODAY.plusDays(2));

        given().header("Authorization", bearer("outro"))
                        .when().post(BOOKINGS + "/" + booking.id() + "/check-in")
                        .then().statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void requiresAuthentication() {
        final var booking = create("lifecycle-auth", TODAY, TODAY.plusDays(2));

        given().when().post(BOOKINGS + "/" + booking.id() + "/check-in")
                        .then().statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    private BookingResponse create(final String key, final LocalDate checkIn, final LocalDate checkOut) {
        return given()
                        .header("Authorization", bearer("usuario"))
                        .header("Idempotency-Key", key)
                        .contentType(ContentType.JSON)
                        .body(new CreateBookingRequest(1, 1, 1, checkIn, checkOut, 1, 1))
                        .when().post(BOOKINGS)
                        .then().statusCode(HttpStatus.SC_CREATED)
                        .extract().as(BookingResponse.class);
    }

    private io.restassured.response.Response post(final java.util.UUID id, final String action, final int expected) {
        return given()
                        .header("Authorization", bearer("usuario"))
                        .when().post(BOOKINGS + "/" + id + action)
                        .then().statusCode(expected)
                        .extract().response();
    }

    private String bearer(final String subject) {
        return "Bearer " + this.tokenBuilder.createJWT(subject, 86400000L);
    }
}

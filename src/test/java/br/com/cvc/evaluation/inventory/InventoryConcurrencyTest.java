package br.com.cvc.evaluation.inventory;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import br.com.cvc.evaluation.booking.Booking;
import br.com.cvc.evaluation.booking.BookingRepository;
import br.com.cvc.evaluation.booking.dto.CreateBookingRequest;
import br.com.cvc.evaluation.extensions.TokenBuilder;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Overbooking: N clientes diferentes disputando o mesmo quarto na mesma noite.
 *
 * <p>Com capacidade 1, exatamente um deve ganhar. O desempate é a condição
 * {@code reserved + 1 <= capacity} no UPDATE — nenhum lock em Java.</p>
 */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class InventoryConcurrencyTest {
    private static final String BOOKINGS = "/api/v1/bookings";
    private static final int CONCURRENT_REQUESTS = 6;
    private static final LocalDate CHECK_IN = LocalDate.of(2027, 3, 10);

    private final TokenBuilder tokenBuilder = new TokenBuilder();

    @Inject
    BookingRepository bookings;

    @Inject
    InventoryRepository inventory;

    @BeforeEach
    @Transactional
    void cleanUp() {
        Booking.deleteAll();
        this.inventory.deleteAll();
    }

    @Test
    void onlyOneCustomerGetsTheLastRoom() throws Exception {
        final var token = "Bearer " + this.tokenBuilder.createJWT("usuario", 86400000L);
        final var start = new CountDownLatch(1);
        final var pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        try {
            final List<Callable<Response>> tasks = new ArrayList<>();
            for (var i = 0; i < CONCURRENT_REQUESTS; i++) {
                final var key = "overbook-" + i; // chaves diferentes: são clientes diferentes
                tasks.add(() -> {
                    start.await();
                    return given()
                                    .header("Authorization", token)
                                    .header("Idempotency-Key", key)
                                    .contentType(ContentType.JSON)
                                    .body(new CreateBookingRequest(1, 1, CHECK_IN, CHECK_IN.plusDays(2), 1, 1))
                                    .when().post(BOOKINGS);
                });
            }

            final var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();

            final var responses = new ArrayList<Response>();
            for (final var future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }

            final var created = responses.stream().filter(r -> r.statusCode() == HttpStatus.SC_CREATED).count();
            final var unavailable = responses.stream()
                            .filter(r -> r.statusCode() == HttpStatus.SC_CONFLICT)
                            .count();
            final var storedBookings = QuarkusTransaction.requiringNew().call(() -> this.bookings.count());
            final var reservedFirstNight = QuarkusTransaction.requiringNew()
                            .call(() -> this.inventory.reserved(1, 1, CHECK_IN));

            assertAll("overbooking",
                            () -> assertEquals(1, created, "exatamente uma reserva criada"),
                            () -> assertEquals(CONCURRENT_REQUESTS - 1, unavailable, "as demais recebem 409"),
                            () -> assertEquals(1L, storedBookings, "uma única reserva persistida"),
                            () -> assertEquals(1, reservedFirstNight, "reserved <= capacity"));
        } finally {
            pool.shutdownNow();
        }
    }
}

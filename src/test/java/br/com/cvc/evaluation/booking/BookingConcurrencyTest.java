package br.com.cvc.evaluation.booking;

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

import br.com.cvc.evaluation.booking.dto.CreateBookingRequest;
import br.com.cvc.evaluation.extensions.TokenBuilder;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import br.com.cvc.evaluation.inventory.InventoryRepository;
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
 * A corrida de verdade: N requisições simultâneas com a mesma Idempotency-Key.
 *
 * <p>O desempate é a constraint única no banco. Quem perde a corrida não recebe 409 —
 * relê a reserva da vencedora numa transação nova e responde 200 com ela (ADR-003).</p>
 */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class BookingConcurrencyTest {
    private static final String BOOKINGS = "/api/v1/bookings";
    private static final int CONCURRENT_REQUESTS = 8;
    private static final LocalDate CHECK_IN = LocalDate.of(2027, 1, 10);

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
    void concurrentRequestsWithTheSameKeyCreateExactlyOneBooking() throws Exception {
        final var body = new CreateBookingRequest(1, 1, CHECK_IN, CHECK_IN.plusDays(2), 1, 1);
        final var token = "Bearer " + this.tokenBuilder.createJWT("usuario", 86400000L);
        final var start = new CountDownLatch(1);
        final var pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        try {
            final List<Callable<Response>> tasks = new ArrayList<>();
            for (var i = 0; i < CONCURRENT_REQUESTS; i++) {
                tasks.add(() -> {
                    start.await();
                    return given()
                                    .header("Authorization", token)
                                    .header("Idempotency-Key", "key-race")
                                    .contentType(ContentType.JSON)
                                    .body(body)
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
            final var replayed = responses.stream().filter(r -> r.statusCode() == HttpStatus.SC_OK).count();
            final var ids = responses.stream().map(r -> r.jsonPath().getString("id")).distinct().toList();
            final var stored = QuarkusTransaction.requiringNew().call(() -> this.repository.count());

            assertAll("race",
                            () -> assertEquals(1, created, "exatamente uma criação"),
                            () -> assertEquals(CONCURRENT_REQUESTS - 1, replayed, "as demais são replay"),
                            () -> assertEquals(1, ids.size(), "todas respondem a mesma reserva: " + ids),
                            () -> assertEquals(1L, stored, "uma única linha persistida"));
        } finally {
            pool.shutdownNow();
        }
    }
}

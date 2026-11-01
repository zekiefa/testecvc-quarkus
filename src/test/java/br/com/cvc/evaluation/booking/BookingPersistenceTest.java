package br.com.cvc.evaluation.booking;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Garante que o schema da V1 e o mapeamento JPA concordam entre si: round-trip do
 * snapshot de preço em JSONB, chave de idempotência única e constraints no banco.
 */
@QuarkusTest
class BookingPersistenceTest {

    @Inject
    BookingRepository repository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        Booking.deleteAll();
    }

    @Test
    @Transactional
    void persistsAndReloadsWithFrozenPrice() {
        final var booking = sample("key-1");

        repository.persist(booking);
        repository.flush();
        repository.getEntityManager().clear();

        final var reloaded = repository.findBookingById(booking.id).orElseThrow();

        assertAll("booking-reload",
                        () -> assertEquals("key-1", reloaded.idempotencyKey),
                        () -> assertEquals(BookingStatus.CONFIRMED, reloaded.status),
                        () -> assertEquals("BRL", reloaded.currency),
                        () -> assertEquals(1, reloaded.adults),
                        () -> assertEquals(1, reloaded.children),
                        () -> assertEquals(0, new BigDecimal("2889.00").compareTo(reloaded.totalAmount)),
                        () -> assertEquals(2, reloaded.nightlyBreakdown.size()),
                        () -> assertEquals("2026-11-10", reloaded.nightlyBreakdown.get(0).stayDate()),
                        () -> assertEquals(0, new BigDecimal("963.00")
                                        .compareTo(reloaded.nightlyBreakdown.get(0).adultAmount())),
                        () -> assertEquals(0, new BigDecimal("481.50")
                                        .compareTo(reloaded.nightlyBreakdown.get(0).childAmount())),
                        () -> assertNotNull(reloaded.createdAt),
                        () -> assertNotNull(reloaded.updatedAt),
                        () -> assertEquals(0L, reloaded.version));
    }

    @Test
    @Transactional
    void rejectsDuplicateIdempotencyKey() {
        repository.persist(sample("same-key"));
        repository.flush();

        repository.persist(sample("same-key"));

        assertThrows(PersistenceException.class, repository::flush);
    }

    @Test
    @Transactional
    void schemaCarriesTheExpectedConstraints() {
        final var constraints = repository.getEntityManager()
                        .createNativeQuery("select conname from pg_constraint where conrelid = 'booking'::regclass",
                                        String.class)
                        .getResultList();

        assertTrue(constraints.contains("booking_idempotency_key_unique"),
                        "UNIQUE(idempotency_key) deve existir, veio: " + constraints);
        assertTrue(constraints.contains("booking_period_valid"),
                        "CHECK (check_out > check_in) deve existir, veio: " + constraints);
    }

    @Test
    @Transactional
    void flywayRecordedVersionOne() {
        final var versions = repository.getEntityManager()
                        .createNativeQuery("select version from flyway_schema_history where success", String.class)
                        .getResultList();

        assertTrue(versions.contains("1"), "a migração V1 deve constar no histórico, veio: " + versions);
    }

    @Test
    @Transactional
    void incrementsVersionOnUpdate() {
        final var booking = sample("key-version");
        repository.persist(booking);
        repository.flush();

        final var initialVersion = booking.version;
        booking.transitionTo(BookingStatus.CANCELLED);
        repository.flush();

        assertEquals(initialVersion + 1L, booking.version, "@Version deve subir a cada update");
    }

    @Test
    @Transactional
    void rejectsUpdateBasedOnStaleVersion() {
        final var booking = sample("key-stale");
        repository.persist(booking);
        repository.flush();
        repository.getEntityManager().clear();

        final var loaded = repository.findBookingById(booking.id).orElseThrow();

        // Simula outra transação que já mudou a linha (versão 0 -> 1).
        repository.getEntityManager()
                        .createNativeQuery("update booking set version = version + 1, status = 'CANCELLED' where id = :id")
                        .setParameter("id", booking.id)
                        .executeUpdate();

        loaded.transitionTo(BookingStatus.CHECKED_IN);

        assertThrows(OptimisticLockException.class, repository::flush);
    }

    private static Booking sample(final String idempotencyKey) {
        final var checkIn = LocalDate.of(2026, 11, 10);
        final var breakdown = List.of(
                        new NightlyRate("2026-11-10", new BigDecimal("963.00"), new BigDecimal("481.50")),
                        new NightlyRate("2026-11-11", new BigDecimal("963.00"), new BigDecimal("481.50")));

        return Booking.create(idempotencyKey, "usuario", 55, 1, checkIn, checkIn.plusDays(2), 1, 1, "BRL",
                        new BigDecimal("2889.00"), breakdown);
    }
}

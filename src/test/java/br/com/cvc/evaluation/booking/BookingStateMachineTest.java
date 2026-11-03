package br.com.cvc.evaluation.booking;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.cvc.evaluation.exceptions.InvalidBookingStateException;
import org.junit.jupiter.api.Test;

/**
 * Teste puro do grafo de estados — sem CDI, sem banco. Se a máquina de estados precisar
 * de infraestrutura para ser testada, ela está no lugar errado.
 */
class BookingStateMachineTest {

    @Test
    void allowsOnlyTheTransitionsInTheGraph() {
        assertAll("confirmed",
                        () -> assertTrue(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CHECKED_IN)),
                        () -> assertTrue(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED)),
                        () -> assertTrue(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.NO_SHOW)),
                        () -> assertFalse(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CHECKED_OUT),
                                        "check-out exige check-in antes"),
                        () -> assertFalse(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CONFIRMED),
                                        "não transiciona para si mesma"));
    }

    @Test
    void checkedInOnlyAllowsCheckOut() {
        assertAll("checked-in",
                        () -> assertEquals(1, BookingStatus.CHECKED_IN.allowedTransitions().size()),
                        () -> assertTrue(BookingStatus.CHECKED_IN.canTransitionTo(BookingStatus.CHECKED_OUT)),
                        () -> assertFalse(BookingStatus.CHECKED_IN.canTransitionTo(BookingStatus.CANCELLED),
                                        "cancelar depois do check-in não é permitido"));
    }

    @Test
    void terminalStatesDoNotMove() {
        assertAll("terminal",
                        () -> assertTrue(BookingStatus.CHECKED_OUT.isTerminal()),
                        () -> assertTrue(BookingStatus.CANCELLED.isTerminal()),
                        () -> assertTrue(BookingStatus.NO_SHOW.isTerminal()),
                        () -> assertFalse(BookingStatus.CONFIRMED.isTerminal()),
                        () -> assertFalse(BookingStatus.CHECKED_IN.isTerminal()),
                        () -> assertTrue(BookingStatus.CANCELLED.allowedTransitions().isEmpty()));
    }

    @Test
    void entityAppliesValidTransition() {
        final var booking = sample();

        booking.transitionTo(BookingStatus.CHECKED_IN);

        assertEquals(BookingStatus.CHECKED_IN, booking.status);
    }

    @Test
    void entityRejectsInvalidTransition() {
        final var booking = sample();
        booking.transitionTo(BookingStatus.CANCELLED);

        assertThrows(InvalidBookingStateException.class,
                        () -> booking.transitionTo(BookingStatus.CHECKED_IN),
                        "reserva cancelada não faz check-in");
    }

    @Test
    void ownershipComesFromTheStoredSubject() {
        final var booking = sample();

        assertAll("owner",
                        () -> assertTrue(booking.isOwnedBy("usuario")),
                        () -> assertFalse(booking.isOwnedBy("outro")));
    }

    private static Booking sample() {
        final var checkIn = LocalDate.of(2026, 11, 10);
        return Booking.create("key-state", "usuario", 55, 1, checkIn, checkIn.plusDays(2), 1, 1, "BRL",
                        new BigDecimal("2835.00"),
                        List.of(new NightlyRate("2026-11-10", new BigDecimal("945.00"), new BigDecimal("472.50")),
                                        new NightlyRate("2026-11-11", new BigDecimal("945.00"),
                                                        new BigDecimal("472.50"))));
    }
}

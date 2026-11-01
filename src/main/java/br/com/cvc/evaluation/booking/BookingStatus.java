package br.com.cvc.evaluation.booking;

import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida da reserva e as transições permitidas (docs/arquitetura.md §3.4).
 *
 * <p>O grafo é fechado de propósito: qualquer transição fora dele é conflito (409), não
 * "estado inesperado". Estados finais não voltam — reabrir uma reserva exige cancelar e
 * criar outra, o que mantém a auditoria honesta.</p>
 *
 * <pre>
 *   CONFIRMED ──▶ CHECKED_IN ──▶ CHECKED_OUT
 *       ├──▶ CANCELLED
 *       └──▶ NO_SHOW
 * </pre>
 */
public enum BookingStatus {
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED,
    NO_SHOW;

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
                    CONFIRMED, Set.of(CHECKED_IN, CANCELLED, NO_SHOW),
                    CHECKED_IN, Set.of(CHECKED_OUT),
                    CHECKED_OUT, Set.of(),
                    CANCELLED, Set.of(),
                    NO_SHOW, Set.of());

    public boolean canTransitionTo(final BookingStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    public Set<BookingStatus> allowedTransitions() {
        return ALLOWED.getOrDefault(this, Set.of());
    }
}

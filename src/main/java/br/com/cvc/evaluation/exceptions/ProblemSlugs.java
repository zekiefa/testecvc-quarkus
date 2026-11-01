package br.com.cvc.evaluation.exceptions;

import java.util.Locale;

/** Deriva o {@code type} do RFC 7807 do nome da exceção: {@code BookingPeriodInvalidException} → {@code booking-period-invalid}. */
final class ProblemSlugs {

    private ProblemSlugs() {
    }

    static String of(final Class<? extends Throwable> type) {
        final var name = type.getSimpleName().replaceAll("Exception$", "");
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}

package br.com.cvc.evaluation.extensions;

import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pedaços de JSON que os stubs do broker compartilham.
 *
 * <p>A lista de noites é o mesmo formato em cotação e em busca; estava copiada nos dois e
 * mudaria em dois lugares.</p>
 */
final class BrokerJson {

    static final String ADULT = "900.00";
    static final String CHILD = "450.00";

    private BrokerJson() {
    }

    static String nights(final LocalDate checkIn, final long nights) {
        return Stream.iterate(checkIn, date -> date.plusDays(1)).limit(nights)
                        .map(date -> "{\"date\":\"%s\",\"adult\":%s,\"child\":%s}".formatted(date, ADULT, CHILD))
                        .collect(Collectors.joining(","));
    }
}

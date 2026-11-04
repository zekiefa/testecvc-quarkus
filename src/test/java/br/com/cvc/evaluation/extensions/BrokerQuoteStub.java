package br.com.cvc.evaluation.extensions;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

/**
 * Faz o papel do broker v1 nos testes: devolve uma cotação com <b>as datas pedidas</b>.
 *
 * <p>Um stub estático obrigaria todo teste a usar o mesmo período e esconderia o contrato
 * (o broker publica tarifa por data). Aqui o corpo é construído a partir da requisição, com
 * tarifas líquidas fixas de 900/450 — as mesmas do fixture legado, para os totais esperados
 * continuarem legíveis.</p>
 */
public class BrokerQuoteStub implements ResponseDefinitionTransformerV2 {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String ADULT = "900.00";
    private static final String CHILD = "450.00";
    private static final long FAILING_HOTEL = 999L;
    /** Unico hotel/quarto que o stub conhece: os testes dos demais casos usam ids fora daqui. */
    private static final long KNOWN_HOTEL = 1L;
    private static final long KNOWN_ROOM = 1L;
    /** Plano que simula periodo sem tarifa publicada no broker (409). */
    private static final long UNAVAILABLE_PLAN = 99L;
    /** Hoteis que violam o contrato de proposito, para exercitar as guardas da ACL. */
    private static final long INCOMPLETE_QUOTE_HOTEL = 998L;
    private static final long WRONG_CURRENCY_HOTEL = 997L;

    @Override
    public String getName() {
        return "broker-quote";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public ResponseDefinition transform(final ServeEvent serveEvent) {
        try {
            final var body = MAPPER.readTree(serveEvent.getRequest().getBodyAsString());
            final long hotelId = body.path("hotelId").asLong();

            // mesmo hotel que falha no contrato legado: exercita o 503
            if (hotelId == FAILING_HOTEL) {
                return failure();
            }

            final var checkIn = LocalDate.parse(body.path("checkIn").asText());
            final var checkOut = LocalDate.parse(body.path("checkOut").asText());
            final long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
            if (nights <= 0) {
                return failure();
            }

            // violacoes de contrato, de proposito: exercitam as guardas da ACL
            if (hotelId == INCOMPLETE_QUOTE_HOTEL) {
                return quote("BRL", checkIn, 1);
            }
            if (hotelId == WRONG_CURRENCY_HOTEL) {
                return quote("USD", checkIn, nights);
            }

            // como o broker: id desconhecido e 404 com type estavel, nao cotacao inventada
            if (hotelId != KNOWN_HOTEL) {
                return notFound("hotel-not-found");
            }
            if (body.path("roomTypeId").asLong() != KNOWN_ROOM) {
                return notFound("room-not-found");
            }
            if (body.path("ratePlanId").asLong() == UNAVAILABLE_PLAN) {
                return incompleteRatePeriod();
            }

            return quote("BRL", checkIn, nights);
        } catch (final Exception e) {
            return failure();
        }
    }

    private static ResponseDefinition quote(final String currency, final LocalDate checkIn, final long nights) {
        final var nightEntries = Stream.iterate(checkIn, date -> date.plusDays(1)).limit(nights)
                        .map(date -> "{\"date\":\"%s\",\"adult\":%s,\"child\":%s}".formatted(date, ADULT, CHILD))
                        .collect(Collectors.joining(","));

        final var json = """
                        {"currency":"%s","totalAmount":0,"nights":[%s],
                         "updatedAt":"2026-11-03T12:00:00Z","staleAfter":"2026-11-03T12:15:00Z",
                         "dataSource":"SEED"}""".formatted(currency, nightEntries);

        return aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(json)
                        .build();
    }

    private static ResponseDefinition incompleteRatePeriod() {
        return aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"type\":\"https://broker.testecvc.example/problems/incomplete-rate-period\","
                                        + "\"status\":409,\"missingDates\":[\"2027-04-11\"]}")
                        .build();
    }

    private static ResponseDefinition notFound(final String slug) {
        return aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"type\":\"https://broker.testecvc.example/problems/%s\",\"status\":404}"
                                        .formatted(slug))
                        .build();
    }

    private static ResponseDefinition failure() {
        return aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"type\":\"about:blank\",\"status\":500}")
                        .build();
    }
}

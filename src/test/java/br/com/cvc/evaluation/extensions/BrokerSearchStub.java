package br.com.cvc.evaluation.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

/**
 * Faz o papel do {@code GET /v1/search} do broker: devolve dois hotéis com as noites do
 * período pedido. As datas vêm da requisição — como no broker, o preço é por data.
 */
public class BrokerSearchStub implements ResponseDefinitionTransformerV2 {

    private static final String KNOWN_CITY = "serra-negra";
    private static final String ADULT = "900.00";
    private static final String CHILD = "450.00";
    /** Frescor sempre no futuro: o consumidor descarta o que o broker marca como vencido. */
    private static final String STALE_AFTER = java.time.Instant.now().plusSeconds(900).toString();

    @Override
    public String getName() {
        return "broker-search";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public ResponseDefinition transform(final ServeEvent serveEvent) {
        final var request = serveEvent.getRequest();
        final var cityCode = request.queryParameter("cityCode").firstValue();
        final var checkIn = LocalDate.parse(request.queryParameter("checkIn").firstValue());
        final var checkOut = LocalDate.parse(request.queryParameter("checkOut").firstValue());
        final long nights = ChronoUnit.DAYS.between(checkIn, checkOut);

        // cenarios de erro do broker, para exercitar o mapeamento da ACL
        if ("rejeitado".equals(cityCode)) {
            return aResponse().withStatus(400)
                            .withHeader("Content-Type", "application/problem+json")
                            .withBody("{\"type\":\"about:blank\",\"status\":400}")
                            .build();
        }
        if ("falha".equals(cityCode)) {
            return aResponse().withStatus(500)
                            .withHeader("Content-Type", "application/problem+json")
                            .withBody("{\"type\":\"about:blank\",\"status\":500}")
                            .build();
        }
        if ("vencido".equals(cityCode) && nights > 0) {
            // oferta que o proprio broker marca como vencida: o consumidor deve descartar
            return aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                            {"cityCode":"vencido","items":[
                                              {"hotelId":1,"hotelName":"Vencido","cityCode":"vencido",
                                               "cityName":"Serra Negra","roomTypeId":1,"roomTypeName":"X",
                                               "ratePlanId":1,"ratePlanName":"Y","board":"BB",
                                               "cancellationPolicy":"FLEXIBLE","totalAmount":0,"currency":"BRL",
                                               "updatedAt":"2020-01-01T00:00:00Z","staleAfter":"2020-01-01T00:15:00Z",
                                               "dataSource":"SEED","nights":[{"date":"2027-04-10","adult":900.00,"child":450.00}]}]}""")
                            .build();
        }
        if ("sem-noites".equals(cityCode) && nights > 0) {
            return aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                            {"cityCode":"sem-noites","items":[
                                              {"hotelId":9,"hotelName":"Sem Noites","cityCode":"sem-noites",
                                               "cityName":"Serra Negra","roomTypeId":9,"roomTypeName":"X",
                                               "ratePlanId":9,"ratePlanName":"Y","board":"RO",
                                               "cancellationPolicy":"FLEXIBLE","totalAmount":0,
                                               "currency":"BRL","dataSource":"SEED"}]}""")
                            .build();
        }
        if (!KNOWN_CITY.equals(cityCode) || nights <= 0) {
            return aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"cityCode\":\"%s\",\"items\":[]}".formatted(cityCode))
                            .build();
        }

        final var nightEntries = Stream.iterate(checkIn, date -> date.plusDays(1)).limit(nights)
                        .map(date -> "{\"date\":\"%s\",\"adult\":%s,\"child\":%s}".formatted(date, ADULT, CHILD))
                        .collect(Collectors.joining(","));

        final var json = """
                        {"cityCode":"%s","checkIn":"%s","checkOut":"%s","adults":1,"children":1,
                         "items":[
                          {"hotelId":1,"hotelName":"Hotel Serra Verde","cityCode":"serra-negra",
                           "cityName":"Serra Negra","roomTypeId":1,"roomTypeName":"Standard Casal",
                           "ratePlanId":1,"ratePlanName":"Café da manhã — flexível","board":"BB",
                           "cancellationPolicy":"FLEXIBLE","totalAmount":0,"currency":"BRL",
                           "updatedAt":"2026-11-03T12:00:00Z","staleAfter":"%s",
                           "dataSource":"SEED","nights":[%s]},
                          {"hotelId":2,"hotelName":"Pousada Vale das Águas","cityCode":"serra-negra",
                           "cityName":"Serra Negra","roomTypeId":3,"roomTypeName":"Família",
                           "ratePlanId":5,"ratePlanName":"Somente quarto","board":"RO",
                           "cancellationPolicy":"NON_REFUNDABLE","totalAmount":0,"currency":"BRL",
                           "updatedAt":"2026-11-03T12:00:00Z","staleAfter":"%s",
                           "dataSource":"SEED","nights":[%s]}
                         ]}""".formatted(cityCode, checkIn, checkOut, STALE_AFTER, nightEntries, STALE_AFTER,
                        nightEntries);

        return aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(json)
                        .build();
    }
}

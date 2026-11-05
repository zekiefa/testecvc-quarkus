package br.com.cvc.evaluation.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

/**
 * Faz o papel do {@code GET /v1/hotels/{id}}: catálogo sem preço (o preço vem da cotação).
 * Só o hotel 1 existe; os demais respondem 404 com {@code type} estável, como o broker.
 */
public class BrokerHotelDetailStub implements ResponseDefinitionTransformerV2 {

    private static final String KNOWN_PATH = "/v1/hotels/1";

    @Override
    public String getName() {
        return "broker-hotel-detail";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public ResponseDefinition transform(final ServeEvent serveEvent) {
        final var path = serveEvent.getRequest().getUrl();

        if (path.endsWith("/999")) {
            return aResponse().withStatus(500)
                            .withHeader("Content-Type", "application/problem+json")
                            .withBody("{\"type\":\"about:blank\",\"status\":500}")
                            .build();
        }

        if (!KNOWN_PATH.equals(path)) {
            return aResponse().withStatus(404)
                            .withHeader("Content-Type", "application/problem+json")
                            .withBody("{\"type\":\"https://broker.testecvc.example/problems/hotel-not-found\","
                                            + "\"status\":404}")
                            .build();
        }

        return aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                        {"id":1,"name":"Hotel Serra Verde","cityCode":"serra-negra",
                                         "cityName":"Serra Negra","dataSource":"SEED",
                                         "updatedAt":"2026-11-03T12:00:00Z",
                                         "roomTypes":[{"id":1,"name":"Standard Casal","maxAdults":2,"maxChildren":2,
                                           "ratePlans":[
                                             {"id":1,"name":"Café da manhã — flexível","board":"BB",
                                              "cancellationPolicy":"FLEXIBLE"},
                                             {"id":2,"name":"Somente quarto","board":"RO",
                                              "cancellationPolicy":"NON_REFUNDABLE"}]}]}""")
                        .build();
    }
}

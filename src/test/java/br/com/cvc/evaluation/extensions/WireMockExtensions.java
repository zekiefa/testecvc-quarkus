package br.com.cvc.evaluation.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class WireMockExtensions implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options()
                        .extensions(new BrokerQuoteStub(), new BrokerSearchStub(), new BrokerHotelDetailStub()));
        wireMockServer.start();

        stubQuoteV1();
        stubSearchV1();
        stubHotelDetailV1();

        return Map.of(
                        "quarkus.rest-client.\"broker-v1\".url",
                        wireMockServer.baseUrl());
    }

    @Override
    public void stop() {
        if (null != wireMockServer) {
            wireMockServer.stop();
        }
    }

    /** Cotacao do contrato v1: o corpo e montado a partir da requisicao (ver BrokerQuoteStub). */
    private void stubQuoteV1() {
        wireMockServer.stubFor(post(urlEqualTo("/v1/quotes"))
                        .willReturn(aResponse().withTransformers("broker-quote")));
    }

    /** Busca do contrato v1: corpo montado a partir dos parametros da requisicao. */
    /** Detalhe do hotel no contrato v1. */
    private void stubHotelDetailV1() {
        wireMockServer.stubFor(get(urlPathMatching("/v1/hotels/[0-9]+"))
                        .willReturn(aResponse().withTransformers("broker-hotel-detail")));
    }

    private void stubSearchV1() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/search"))
                        .willReturn(aResponse().withTransformers("broker-search")));
    }

}

package br.com.cvc.evaluation.extensions;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static javax.json.Json.createParser;
import static org.junit.jupiter.api.Assertions.fail;

import javax.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class WireMockExtensions implements QuarkusTestResourceLifecycleManager {
    private static final String HOTELS_JSON_FILE = "/hotels.json";
    private static final String BASE_PATH = "/hotels";

    private WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer();
        wireMockServer.start();

        stubHotels();

        return Collections
                        .singletonMap("quarkus.rest-client.\"br.com.cvc.evaluation.broker.BrokerService\".url",
                                        wireMockServer.baseUrl());
    }

    @Override
    public void stop() {
        if (null != wireMockServer) {
            wireMockServer.stop();
        }
    }

    private void stubHotels() {
        try (InputStream is = WireMockExtensions.class.getResourceAsStream(HOTELS_JSON_FILE)) {
            assert is != null;
            String hotels = new String(is.readAllBytes());

            // Stub for full list of hotels:
            wireMockServer.stubFor(get(urlPathMatching(BASE_PATH.concat("/avail/([0-9]*)")))
                            .willReturn(okJson(hotels)));

            // Stub for each hotel
            try (StringReader sr = new StringReader(hotels); final var parser = createParser(sr)) {
                parser.next();
                for (JsonValue hotel : parser.getArray()) {
                    final var id = hotel.asJsonObject().getInt("id");

                    wireMockServer.stubFor(get(urlEqualTo(String.format(BASE_PATH.concat("/%d"), id)))
                                    .willReturn(okJson(hotel.toString())));
                }
            }
        } catch (IOException e) {
            fail("Could not configure Wiremock server. Caused by: " + e.getMessage());
        }
    }
}

package br.com.cvc.evaluation.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import br.com.cvc.evaluation.extensions.TokenBuilder;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Correlação entre requisição, log e corpo de erro: o mesmo id deve aparecer no header
 * de resposta e no {@code traceId} do problem+json.
 */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class RequestIdFilterTest {
    private static final String UUID_V4 = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final TokenBuilder tokenBuilder = new TokenBuilder();

    @Test
    void echoesTheProvidedRequestId() {
        given().header("Authorization", bearer())
                        .header(RequestIdFilter.HEADER, "abc-123")
                        .when().get("/api/v1/hotels/1?checkIn=2027-04-10&checkOut=2027-04-12&adults=1&children=0")
                        .then().statusCode(HttpStatus.SC_OK)
                        .header(RequestIdFilter.HEADER, is("abc-123"));
    }

    @Test
    void generatesOneWhenAbsent() {
        given().header("Authorization", bearer())
                        .when().get("/api/v1/hotels/1?checkIn=2027-04-10&checkOut=2027-04-12&adults=1&children=0")
                        .then().statusCode(HttpStatus.SC_OK)
                        .header(RequestIdFilter.HEADER, matchesPattern(UUID_V4));
    }

    @Test
    void rejectsAValueThatCouldPoisonTheLog() {
        // valor com espaço/quebra não é aceito como id: gera um novo
        given().header("Authorization", bearer())
                        .header(RequestIdFilter.HEADER, "id com espaco")
                        .when().get("/api/v1/hotels/1?checkIn=2027-04-10&checkOut=2027-04-12&adults=1&children=0")
                        .then().statusCode(HttpStatus.SC_OK)
                        .header(RequestIdFilter.HEADER, matchesPattern(UUID_V4));
    }

    @Test
    void errorCarriesTheSameTraceId() {
        final var response = given().header("Authorization", bearer())
                        .header(RequestIdFilter.HEADER, "trace-do-erro")
                        .when().get("/api/v1/bookings/" + UUID.randomUUID())
                        .then().statusCode(HttpStatus.SC_NOT_FOUND)
                        .extract().response();

        assertEquals("trace-do-erro", response.header(RequestIdFilter.HEADER));
        assertEquals("trace-do-erro", response.jsonPath().getString("traceId"),
                        "o corpo do erro deve permitir achar a linha de log");
    }

    private String bearer() {
        return "Bearer " + this.tokenBuilder.createJWT("usuario", 86400000L);
    }
}

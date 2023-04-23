package br.com.cvc.evaluation.endpoint;

import static io.restassured.RestAssured.given;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import br.com.cvc.evaluation.domain.Hotel;
import br.com.cvc.evaluation.extensions.TokenBuilder;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class BookingEndpointTest {
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private final TokenBuilder tokenBuilder = new TokenBuilder();
    @Test
    void testFind() {
        // Arranges
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        // Act | Assert
        given()
                        .headers("Authorization", "Bearer " +
                                        tokenBuilder.createJWT("usuario", 86400000L))
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .log().all()
                        .when().get(String.format("/api/v1/booking/55/%s/%s/1/1", checkin, checkout))
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(Hotel[].class);
    }

    @Test
    void testFindWithoutToken() {
        // Act | Asserts
        given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .log().all()
                        .when()
                        .get("/api/v1/booking/1/2/3/1/1")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_UNAUTHORIZED);
    }
}

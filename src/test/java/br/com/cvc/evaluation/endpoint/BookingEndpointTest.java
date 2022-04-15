package br.com.cvc.evaluation.endpoint;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import br.com.cvc.evaluation.domain.Hotel;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
class BookingEndpointTest {
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String HOTELS_JSON_FILE = "/hotels.json";

    @Test
    void testFind() {
        // Arranges
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        // Act | Assert
        given()
                        .when().get(String.format("/booking/55/%s/%s/1/1", checkin, checkout))
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(Hotel[].class);
    }
}

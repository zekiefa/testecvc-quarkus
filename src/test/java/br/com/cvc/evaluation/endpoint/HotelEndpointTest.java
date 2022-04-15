package br.com.cvc.evaluation.endpoint;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import br.com.cvc.evaluation.domain.Hotel;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
public class HotelEndpointTest {

    @Test
    void testFind() {
        final var idHotel = 1;

        final var response = given()
                        .when().get(String.format("/hotels/%d", idHotel))
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(Hotel.class);

        assertAll("success",
                        () -> assertNotNull(response),
                        () -> assertThat(response.getId(), is(idHotel))
        );
    }
}

package br.com.cvc.evaluation.endpoint;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
public class HotelEndpointTest {
    private final TokenBuilder tokenBuilder = new TokenBuilder();
    @Test
    void testFind() {
        final var idHotel = 1;

        final var response = given()
                        .headers("Authorization", "Bearer "+
                                        tokenBuilder.createJWT("usuario", 86400000L))
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .log().all().when().get(String.format("/hotels/%d", idHotel))
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(Hotel.class);

        assertAll("success",
                        () -> assertNotNull(response),
                        () -> assertThat(response.getId(), is(idHotel))
        );
    }

    @Test
    void testFindWithoutToken() {
        // Act | Asserts
        given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .log().all()
                        .when()
                        .get("/hotels/1")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_UNAUTHORIZED);
    }
}

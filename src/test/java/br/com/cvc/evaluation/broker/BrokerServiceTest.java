package br.com.cvc.evaluation.broker;

import static io.restassured.RestAssured.given;

import br.com.cvc.evaluation.extensions.WireMockExtensions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
public class BrokerServiceTest {

    @Test
    public void testGetHotelDetailsEndpoint() {
        given()
                        .when().get("/hotels/1")
                        .then()
                        .statusCode(200);
    }
}

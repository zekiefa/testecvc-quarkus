package br.com.cvc.evaluation.operations;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Contrato e operação (M3): o que um operador e um consumidor da API precisam enxergar
 * sem ler o código.
 */
@QuarkusTest
class OperationsEndpointTest {

    @Test
    void healthReportsUp() {
        given().when().get("/q/health")
                        .then().statusCode(HttpStatus.SC_OK)
                        .body("status", is("UP"));
    }

    @Test
    void readinessIncludesTheDatabaseCheck() {
        given().when().get("/q/health/ready")
                        .then().statusCode(HttpStatus.SC_OK)
                        .body("status", is("UP"))
                        .body("checks.name", hasItem(containsString("Database")));
    }

    @Test
    void openapiExposesTheBookingContract() {
        given().when().get("/q/openapi?format=json")
                        .then().statusCode(HttpStatus.SC_OK)
                        .body("info.title", is("Testecvc Hotel Booking API"))
                        .body("paths", hasKey("/api/v1/bookings"))
                        .body("paths", hasKey("/api/v1/bookings/{id}/cancel"))
                        .body("paths", hasKey("/api/v1/hotels/{id}/availability"));
    }

    @Test
    void metricsExposeHttpRequests() {
        // uma chamada antes, para haver amostra de requisição
        given().when().get("/q/health");

        given().when().get("/q/metrics")
                        .then().statusCode(HttpStatus.SC_OK)
                        .body(containsString("http_server_requests"));
    }
}

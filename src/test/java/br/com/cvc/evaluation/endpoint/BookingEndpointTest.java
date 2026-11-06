package br.com.cvc.evaluation.endpoint;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import br.com.cvc.evaluation.catalog.HotelOffer;
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
        final var offers = given()
                        .headers("Authorization", "Bearer " +
                                        tokenBuilder.createJWT("usuario", 86400000L))
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .log().all()
                        .when().get(String.format("/api/v1/booking/serra-negra/%s/%s/1/1", checkin, checkout))
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(HotelOffer[].class);

        assertEquals(2, offers.length, "dois hoteis no resultado");
        for (final var offer : offers) {
            assertEquals(3, offer.nights().size(), "tres noites detalhadas");
            assertEquals(0, new BigDecimal("4526.28").compareTo(offer.totalAmount()),
                            "900 + 450 por noite, com 5%: 4526.28");
        }
    }

    @Test
    void offerWithoutNightsStillAnswers() {
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        final var offers = given().headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .when().get(String.format("/api/v1/booking/sem-noites/%s/%s/1/1", checkin, checkout))
                        .then().statusCode(HttpStatus.SC_OK)
                        .extract().as(HotelOffer[].class);

        assertEquals(1, offers.length);
        assertTrue(offers[0].nights().isEmpty(), "sem noites, o detalhamento vem vazio");
    }

    @Test
    void searchWithBrokerRejectionIsBadRequest() {
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        given().headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .when().get(String.format("/api/v1/booking/rejeitado/%s/%s/1/1", checkin, checkout))
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void searchWithBrokerFailureIsUnavailable() {
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        given().headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .when().get(String.format("/api/v1/booking/falha/%s/%s/1/1", checkin, checkout))
                        .then().statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
    }

    @Test
    void invalidCityCodeIsRejected() {
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        given().headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .when().get(String.format("/api/v1/booking/Serra%%20Negra/%s/%s/1/1", checkin, checkout))
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void cityCodeWithControlCharactersIsRejected() {
        // tentativa de injetar linha de log pelo path: o formato do slug corta antes
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        given().headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .when().get(String.format("/api/v1/booking/serra%%0D%%0Anegra/%s/%s/1/1", checkin, checkout))
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void staleOffersAreNotOffered() {
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        final var offers = given().headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .when().get(String.format("/api/v1/booking/vencido/%s/%s/1/1", checkin, checkout))
                        .then().statusCode(HttpStatus.SC_OK)
                        .extract().as(HotelOffer[].class);

        assertEquals(0, offers.length, "oferta vencida nao e ofertada");
    }

    @Test
    void offerCarriesTheDataSource() {
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        given().headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .when().get(String.format("/api/v1/booking/serra-negra/%s/%s/1/1", checkin, checkout))
                        .then().statusCode(HttpStatus.SC_OK)
                        .body("[0].dataSource", org.hamcrest.Matchers.is("SEED"));
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

    @Test
    void testFindWithMalformedDate() {
        // Antes: LocalDate.parse lançava DateTimeParseException -> 500.
        given()
                        .headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .get("/api/v1/booking/55/10-01-2026/2026-01-12/1/1")
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testFindWithZeroNights() {
        // checkout == checkin: zero noites, zero preço.
        final var day = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        given()
                        .headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .get(String.format("/api/v1/booking/55/%s/%s/1/1", day, day))
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testFindWithoutAdults() {
        final var checkin = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        final var checkout = LocalDate.now().plusDays(5).format(DateTimeFormatter.ofPattern(DATE_FORMAT));

        given()
                        .headers("Authorization", "Bearer " + tokenBuilder.createJWT("usuario", 86400000L))
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .when()
                        .get(String.format("/api/v1/booking/55/%s/%s/0/1", checkin, checkout))
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }
}

package br.com.cvc.evaluation.booking;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.time.LocalDate;
import java.util.Map;

import br.com.cvc.evaluation.booking.dto.CreateBookingRequest;
import br.com.cvc.evaluation.extensions.TokenBuilder;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

/**
 * O limite de noites é configuração, não constante: este perfil reduz
 * {@code booking.max-nights} para 5 e prova que o serviço obedece.
 */
@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
@TestProfile(MaxNightsFromConfigTest.ShortPeriodProfile.class)
class MaxNightsFromConfigTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2027, 4, 10);
    private final TokenBuilder tokenBuilder = new TokenBuilder();

    public static class ShortPeriodProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("booking.max-nights", "5");
        }
    }

    @Test
    void periodAboveTheConfiguredLimitIsRejected() {
        given().header("Authorization", bearer())
                        .header("Idempotency-Key", "max-nights-6")
                        .contentType(ContentType.JSON)
                        .body(new CreateBookingRequest(1, 1, 1, CHECK_IN, CHECK_IN.plusDays(6), 1, 1))
                        .when().post("/api/v1/bookings")
                        .then().statusCode(HttpStatus.SC_BAD_REQUEST)
                        .body("detail", containsString("5"));
    }

    @Test
    void periodWithinTheConfiguredLimitIsAccepted() {
        given().header("Authorization", bearer())
                        .header("Idempotency-Key", "max-nights-5")
                        .contentType(ContentType.JSON)
                        .body(new CreateBookingRequest(1, 1, 1, CHECK_IN, CHECK_IN.plusDays(5), 1, 1))
                        .when().post("/api/v1/bookings")
                        .then().statusCode(HttpStatus.SC_CREATED);
    }

    private String bearer() {
        return "Bearer " + this.tokenBuilder.createJWT("usuario", 86400000L);
    }
}

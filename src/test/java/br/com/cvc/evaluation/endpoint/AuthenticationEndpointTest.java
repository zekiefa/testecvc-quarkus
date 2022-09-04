package br.com.cvc.evaluation.endpoint;

import static io.restassured.RestAssured.given;

import javax.inject.Inject;

import br.com.cvc.evaluation.domain.Login;
import br.com.cvc.evaluation.domain.Token;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import br.com.cvc.evaluation.service.TokenService;
import br.com.cvc.evaluation.service.UserService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
public class AuthenticationEndpointTest {
    final static String AUTH = "/auth";

    @Inject
    TokenService tokenService;

    @Inject
    UserService userService;

    @Test
    void testLoginSuccessfully() {
        // Act | Assert
        given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body(new Login("usuario", "senha"))
                        .log().all()
                        .when().post(AUTH)
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(Token.class);

    }

    @Test
    void testLoginForbidden() {
        // Act | Assert
        given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body(new Login("usuario", "senhaErrada"))
                        .log().all()
                        .when().post(AUTH)
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_FORBIDDEN);

    }

    @Test
    void testUserNotFound() {
        // Act | Assert
        given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body(new Login("usuario2", "senha"))
                        .log().all()
                        .when().post(AUTH)
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_NOT_FOUND);

    }
}

package br.com.cvc.evaluation.endpoint;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import br.com.cvc.evaluation.domain.Login;
import br.com.cvc.evaluation.domain.Token;
import br.com.cvc.evaluation.extensions.WireMockExtensions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(WireMockExtensions.class)
public class AuthenticationEndpointTest {
    private static final String AUTH = "/auth";

    @Test
    void testLoginSuccessfully() {
        // Act
        final var token = given()
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

        // Assert: type e token não podem vir trocados (record Token(type, token))
        assertThat(token.type(), is("Bearer"));
        assertThat(token.token(), notNullValue());
    }

    @Test
    void testLoginWithWrongPassword() {
        // Deve ser 401, não 403: a falha é de autenticação, não de autorização.
        given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body(new Login("usuario", "senhaErrada"))
                        .log().all()
                        .when().post(AUTH)
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    void testLoginWithUnknownUser() {
        // Mesmo status e mesma mensagem do caso anterior: não revela se o usuário existe.
        given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body(new Login("usuario2", "senha"))
                        .log().all()
                        .when().post(AUTH)
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    void testLoginWithoutCredentials() {
        given()
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body(new Login(null, null))
                        .log().all()
                        .when().post(AUTH)
                        .then()
                        .assertThat()
                        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }
}

package br.com.cvc.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import javax.inject.Inject;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.auth.principal.DefaultJWTTokenParser;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TokenServiceTest {
    @Inject
    TokenService tokenService;

    @Inject
    JWTAuthContextInfo config;

    final DefaultJWTTokenParser parser = new DefaultJWTTokenParser();

    @Test
    void testGenerateToken() {
        // Asserts
        assertDoesNotThrow(() ->
                        parser.parse(this.tokenService.generateToken("usuario", Set.of("user")),
                                        config));
    }
}

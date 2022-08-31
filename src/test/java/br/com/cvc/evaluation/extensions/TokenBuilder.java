package br.com.cvc.evaluation.extensions;

import java.time.Instant;
import java.util.Set;

import io.smallrye.jwt.build.Jwt;

public class TokenBuilder {

    public String createJWT(final String subject, final Long ttlMillis) {
        final var now = Instant.ofEpochMilli(System.currentTimeMillis());
        return Jwt
                        .issuer("testecvc")
                        .issuedAt(now)
                        .subject(subject)
                        .groups(Set.of("user", "admin"))
                        .expiresAt(now.plusMillis(ttlMillis))
                        .sign(); // mallrye.jwt.sign.key
    }
}

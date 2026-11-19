package br.com.cvc.evaluation.extensions;

import java.time.Instant;
import java.util.Set;

import io.smallrye.jwt.build.Jwt;

public class TokenBuilder {

    /**
     * Cabeçalho pronto para o teste. O prefixo vem daqui: cinco cópias locais deste método
     * divergiram uma vez (uma delas sem o "Bearer ") e o resultado foi um 401 confuso.
     */
    public String bearer(final String subject) {
        return "Bearer " + createJWT(subject, 86_400_000L);
    }

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

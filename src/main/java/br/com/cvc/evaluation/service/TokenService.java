package br.com.cvc.evaluation.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.util.Set;

import br.com.cvc.evaluation.service.provider.JwtProvider;
import io.smallrye.jwt.build.Jwt;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class TokenService {
    @Inject
    JwtProvider jwtProvider;

    public String generateToken(final String username, final Set<String> groups) {
        log.info("Generating token for user {}", username);
        final var now = Instant.ofEpochMilli(System.currentTimeMillis());

        return Jwt.issuer(this.jwtProvider.issuer())
                        .subject(username)
                        .groups(groups)
                        .expiresAt(now.plusMillis(this.jwtProvider.expiration()))
                        .sign();
    }
}

package br.com.cvc.evaluation.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.util.Set;

import br.com.cvc.evaluation.service.provider.JwtProvider;
import io.smallrye.jwt.build.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TokenService {
    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
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

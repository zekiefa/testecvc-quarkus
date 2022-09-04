package br.com.cvc.evaluation.service.provider;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "jwt")
public interface JwtProvider {
    Long expiration();
    String issuer();
}

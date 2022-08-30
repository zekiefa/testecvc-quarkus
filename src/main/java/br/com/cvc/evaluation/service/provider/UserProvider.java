package br.com.cvc.evaluation.service.provider;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "usuario")
public interface UserProvider {
    String login();

    String passwd();
}

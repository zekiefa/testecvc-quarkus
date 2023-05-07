package br.com.cvc.evaluation.service;

import java.util.Optional;
import java.util.Set;

import br.com.cvc.evaluation.domain.Profile;
import br.com.cvc.evaluation.domain.User;
import br.com.cvc.evaluation.service.provider.UserProvider;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserProvider userProvider;

    public UserService(final UserProvider userProvider) {
        this.userProvider = userProvider;
    }

    public Optional<User> findByLogin(final String login) {
        log.info("Finding user by login {}", login);
        // o ideal é ter um banco de dados para consultar o usuário
        if (login.equals(this.userProvider.login())) {
            log.info("User {} found", login);
            return Optional.of(new User(login,
                            BcryptUtil.bcryptHash(this.userProvider.passwd()),
                            Set.of(new Profile(0, "user"))));
        }

        log.info("User {} not found", login);
        return Optional.empty();
    }
}

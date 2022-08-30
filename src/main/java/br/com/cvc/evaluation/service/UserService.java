package br.com.cvc.evaluation.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;

import br.com.cvc.evaluation.domain.Profile;
import br.com.cvc.evaluation.domain.User;
import br.com.cvc.evaluation.service.provider.UserProvider;
import io.quarkus.elytron.security.common.BcryptUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class UserService {
    @Inject
    UserProvider userProvider;

    public Optional<User> findByLogin(final String login) {
        log.info("Finding user by login {}", login);
        // o ideal é ter um banco de dados para consultar o usuário
        if (login.equals(this.userProvider.login())) {
            log.info("User {} found", login);
            return Optional.of(User.builder()
                            .username(login)
                            .password(BcryptUtil.bcryptHash(this.userProvider.passwd()))
                            .profiles(Set.of(Profile.builder().id(0).name("user").build()))
                            .build());
        }

        log.info("User {} not found", login);
        return Optional.empty();
    }
}

package br.com.cvc.evaluation.service;

import java.util.Optional;
import java.util.stream.Collectors;

import br.com.cvc.evaluation.domain.Profile;
import br.com.cvc.evaluation.domain.User;
import br.com.cvc.evaluation.identity.AppUser;
import br.com.cvc.evaluation.identity.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository repository;

    public UserService(final UserRepository repository) {
        this.repository = repository;
    }

    public Optional<User> findByLogin(final String login) {
        log.info("Finding user by login {}", login);
        return this.repository.findByUsername(login)
                        .filter(user -> user.enabled)
                        .map(UserService::toDomain);
    }

    private static User toDomain(final AppUser user) {
        final var profiles = user.roles.stream().map(Profile::new).collect(Collectors.toSet());
        return new User(user.username, user.passwordHash, profiles);
    }
}

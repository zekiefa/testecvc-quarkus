package br.com.cvc.evaluation.identity;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<AppUser, Long> {

    public Optional<AppUser> findByUsername(final String username) {
        return find("username", username).firstResultOptional();
    }
}

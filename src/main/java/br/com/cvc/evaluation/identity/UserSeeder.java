package br.com.cvc.evaluation.identity;

import java.util.Set;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cria o usuário de desenvolvimento na primeira subida, se a tabela estiver vazia.
 *
 * <p>Desligado por padrão ({@code app.seed-user.enabled=false}); dev e test ligam
 * explicitamente. Em produção, identidade vem de provisionamento real — semear senha
 * padrão em produção seria uma porta aberta.</p>
 */
@ApplicationScoped
public class UserSeeder {
    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository repository;
    private final boolean enabled;
    private final String username;
    private final String password;

    public UserSeeder(final UserRepository repository,
                    @ConfigProperty(name = "app.seed-user.enabled", defaultValue = "false") final boolean enabled,
                    @ConfigProperty(name = "app.seed-user.username", defaultValue = "usuario") final String username,
                    @ConfigProperty(name = "app.seed-user.password", defaultValue = "senha") final String password) {
        this.repository = repository;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
    }

    @Transactional
    void onStart(@Observes final StartupEvent event) {
        if (!this.enabled) {
            return;
        }
        if (this.repository.count() > 0) {
            log.debug("Seed skipped: app_user already has rows");
            return;
        }

        final var user = AppUser.create(this.username, BcryptUtil.bcryptHash(this.password), Set.of("user"));
        this.repository.persist(user);
        log.info("Seeded development user '{}' (bcrypt)", this.username);
    }
}

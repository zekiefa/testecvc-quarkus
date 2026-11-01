package br.com.cvc.evaluation.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;

/** Identidade no banco: senha em bcrypt, papel persistido e seeder idempotente. */
@QuarkusTest
class UserPersistenceTest {

    @Inject
    UserRepository repository;

    @Test
    @Transactional
    void seedsADevelopmentUserWithBcryptPassword() {
        final var user = this.repository.findByUsername("usuario").orElseThrow();

        assertAll("seeded-user",
                        () -> assertTrue(user.passwordHash.startsWith("$2"), "deve ser bcrypt"),
                        () -> assertFalse(user.passwordHash.contains("senha"), "senha nunca em claro"),
                        () -> assertTrue(user.roles.contains("user"), "papel persistido em user_profile"),
                        () -> assertTrue(user.enabled));
    }

    @Test
    @Transactional
    void doesNotSeedTwice() {
        assertEquals(1L, this.repository.count(), "o seeder deve ser idempotente");
    }
}

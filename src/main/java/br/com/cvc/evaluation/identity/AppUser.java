package br.com.cvc.evaluation.identity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/** Usuário persistido. A senha é sempre bcrypt — não existe campo para texto claro. */
@Entity
@Table(name = "app_user")
public class AppUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String username;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(nullable = false)
    public boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_profile", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    public Set<String> roles = new HashSet<>();

    public static AppUser create(final String username, final String passwordHash, final Set<String> roles) {
        final var user = new AppUser();
        user.username = username;
        user.passwordHash = passwordHash;
        user.roles = new HashSet<>(roles);
        return user;
    }
}

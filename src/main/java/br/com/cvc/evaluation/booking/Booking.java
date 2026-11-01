package br.com.cvc.evaluation.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Reserva persistida. O schema é criado pelo Flyway (V1) e o Hibernate apenas mapeia.
 *
 * <p>Regras estruturais ficam no banco (período válido, chave de idempotência única,
 * contagens não negativas); as regras de negócio e as transições de estado ficam no
 * domínio (M1.5) e no caso de uso (M1.3).</p>
 */
@Entity
@Table(name = "booking")
public class Booking extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    /** Chave enviada pelo cliente; garante que retry não crie reserva duplicada (ADR-003). */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    public String idempotencyKey;

    /** `sub` do JWT que criou a reserva; base da autorização por dono (ADR-003). */
    @Column(name = "user_subject", nullable = false)
    public String userSubject;

    @Column(name = "hotel_id", nullable = false)
    public Integer hotelId;

    @Column(name = "room_id", nullable = false)
    public Integer roomId;

    @Column(name = "check_in", nullable = false)
    public LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    public LocalDate checkOut;

    @Column(nullable = false)
    public int adults;

    @Column(nullable = false)
    public int children;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public BookingStatus status;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    public BigDecimal totalAmount;

    /** Preço congelado por noite; ver {@link NightlyRate}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nightly_breakdown", nullable = false, columnDefinition = "jsonb")
    public List<NightlyRate> nightlyBreakdown = new ArrayList<>();

    @Version
    @Column(nullable = false)
    public long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /** Fábrica com as invariantes que não dependem do banco. */
    public static Booking create(final String idempotencyKey,
                    final String userSubject,
                    final Integer hotelId,
                    final Integer roomId,
                    final LocalDate checkIn,
                    final LocalDate checkOut,
                    final int adults,
                    final int children,
                    final String currency,
                    final BigDecimal totalAmount,
                    final List<NightlyRate> nightlyBreakdown) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (userSubject == null || userSubject.isBlank()) {
            throw new IllegalArgumentException("userSubject is required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
        if (adults < 1 || children < 0) {
            throw new IllegalArgumentException("adults must be >= 1 and children >= 0");
        }
        if (nightlyBreakdown == null || nightlyBreakdown.isEmpty()) {
            throw new IllegalArgumentException("nightlyBreakdown must not be empty");
        }

        final var booking = new Booking();
        booking.idempotencyKey = idempotencyKey;
        booking.userSubject = userSubject;
        booking.hotelId = hotelId;
        booking.roomId = roomId;
        booking.checkIn = checkIn;
        booking.checkOut = checkOut;
        booking.adults = adults;
        booking.children = children;
        booking.status = BookingStatus.CONFIRMED;
        booking.currency = currency;
        booking.totalAmount = totalAmount;
        booking.nightlyBreakdown = List.copyOf(nightlyBreakdown);
        return booking;
    }
}

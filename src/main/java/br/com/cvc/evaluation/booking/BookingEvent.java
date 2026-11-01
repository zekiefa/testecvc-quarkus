package br.com.cvc.evaluation.booking;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Evento de auditoria da reserva, append-only, gravado na mesma transação que a mudança
 * de estado (ADR-003). Não é Outbox: não existe publicação nem consumidor nesta fase.
 */
@Entity
@Table(name = "booking_event")
public class BookingEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    public Booking booking;

    @Column(nullable = false, length = 40)
    public String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> payload;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    public Instant occurredAt;

    public static BookingEvent of(final Booking booking, final String type, final Map<String, Object> payload) {
        final var event = new BookingEvent();
        event.booking = booking;
        event.type = type;
        event.payload = payload;
        return event;
    }
}

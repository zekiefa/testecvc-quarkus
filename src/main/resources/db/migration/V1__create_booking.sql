-- M1.2 — schema mínimo da reserva (docs/arquitetura.md §3.3)
-- O Flyway é dono do schema; o Hibernate roda com schema-management.strategy=none.

CREATE TABLE booking
(
    id                UUID PRIMARY KEY,
    idempotency_key   TEXT         NOT NULL,
    hotel_id          INTEGER      NOT NULL,
    room_id           INTEGER      NOT NULL,
    check_in          DATE         NOT NULL,
    check_out         DATE         NOT NULL,
    adults            INTEGER      NOT NULL,
    children          INTEGER      NOT NULL,
    status            TEXT         NOT NULL,
    currency          CHAR(3)      NOT NULL,
    total_amount      NUMERIC(12, 2) NOT NULL,
    nightly_breakdown JSONB        NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT booking_idempotency_key_unique UNIQUE (idempotency_key),
    CONSTRAINT booking_period_valid CHECK (check_out > check_in),
    CONSTRAINT booking_adults_positive CHECK (adults >= 1),
    CONSTRAINT booking_children_non_negative CHECK (children >= 0),
    CONSTRAINT booking_total_non_negative CHECK (total_amount >= 0)
);

CREATE INDEX booking_hotel_room_checkin_idx ON booking (hotel_id, room_id, check_in);
CREATE INDEX booking_status_checkout_idx ON booking (status, check_out);

-- Log de auditoria append-only, escrito no mesmo commit da transação (ADR-003).
-- Não é Outbox: não há publicação de mensagem nesta fase.
CREATE TABLE booking_event
(
    id          BIGSERIAL PRIMARY KEY,
    booking_id  UUID        NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    type        TEXT        NOT NULL,
    payload     JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX booking_event_booking_occurred_idx ON booking_event (booking_id, occurred_at);

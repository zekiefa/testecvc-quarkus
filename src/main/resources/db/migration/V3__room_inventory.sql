-- M2.1 — inventário por quarto/noite (docs/arquitetura.md §3.3)
-- Capacidade padrão = 1 por quarto/noite, materializada sob demanda na primeira reserva.

CREATE TABLE room_inventory
(
    hotel_id   INTEGER NOT NULL,
    room_id    INTEGER NOT NULL,
    stay_date  DATE    NOT NULL,
    capacity   INTEGER NOT NULL,
    reserved   INTEGER NOT NULL DEFAULT 0,
    version    BIGINT  NOT NULL DEFAULT 0,

    PRIMARY KEY (hotel_id, room_id, stay_date),
    CONSTRAINT room_inventory_capacity_non_negative CHECK (capacity >= 0),
    CONSTRAINT room_inventory_reserved_bounds CHECK (reserved >= 0 AND reserved <= capacity)
);

CREATE INDEX room_inventory_stay_date_idx ON room_inventory (stay_date);

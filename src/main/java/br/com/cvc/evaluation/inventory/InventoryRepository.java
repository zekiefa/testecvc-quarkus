package br.com.cvc.evaluation.inventory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Operações de inventário em SQL nativo de propósito: são operações de conjunto
 * (upsert e update condicional), que o modelo de entidade não expressa bem e que
 * precisam ser atômicas no banco — não um read-modify-write em Java.
 */
@ApplicationScoped
public class InventoryRepository {
    private static final String HOTEL_ID = "hotelId";
    private static final String ROOM_ID = "roomId";
    private static final String STAY_DATE = "stayDate";

    final EntityManager entityManager;

    public InventoryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Cria as linhas do período com a capacidade padrão, sem tocar nas que já existem. */
    public void materialize(final Integer hotelId, final Integer roomId, final List<LocalDate> nights,
                    final int defaultCapacity) {
        for (final var night : nights) {
            this.entityManager.createNativeQuery("""
                            insert into room_inventory (hotel_id, room_id, stay_date, capacity, reserved, version)
                            values (:hotelId, :roomId, :stayDate, :capacity, 0, 0)
                            on conflict (hotel_id, room_id, stay_date) do nothing
                            """)
                            .setParameter(HOTEL_ID, hotelId)
                            .setParameter(ROOM_ID, roomId)
                            .setParameter(STAY_DATE, night)
                            .setParameter("capacity", defaultCapacity)
                            .executeUpdate();
        }
    }

    /**
     * Reserva uma noite. A condição {@code reserved + 1 <= capacity} é o que impede
     * overbooking: o banco decide, não o Java.
     *
     * @return {@code true} se a linha foi reservada
     */
    public boolean hold(final Integer hotelId, final Integer roomId, final LocalDate night) {
        return this.entityManager.createNativeQuery("""
                        update room_inventory
                           set reserved = reserved + 1, version = version + 1
                         where hotel_id = :hotelId
                           and room_id = :roomId
                           and stay_date = :stayDate
                           and reserved + 1 <= capacity
                        """)
                        .setParameter(HOTEL_ID, hotelId)
                        .setParameter(ROOM_ID, roomId)
                        .setParameter(STAY_DATE, night)
                        .executeUpdate() == 1;
    }

    /** Devolve uma noite ao inventário (cancelamento). */
    public void release(final Integer hotelId, final Integer roomId, final LocalDate night) {
        this.entityManager.createNativeQuery("""
                        update room_inventory
                           set reserved = reserved - 1, version = version + 1
                         where hotel_id = :hotelId
                           and room_id = :roomId
                           and stay_date = :stayDate
                           and reserved > 0
                        """)
                        .setParameter(HOTEL_ID, hotelId)
                        .setParameter(ROOM_ID, roomId)
                        .setParameter(STAY_DATE, night)
                        .executeUpdate();
    }

    /** Noites do período com capacidade ainda disponível (ausência = nenhuma reserva feita). */
    public Map<LocalDate, Integer> availableByNight(final Integer hotelId, final Integer roomId,
                    final LocalDate checkIn, final LocalDate checkOut) {
        @SuppressWarnings("unchecked")
        final List<Object[]> rows = this.entityManager.createNativeQuery("""
                        select stay_date, capacity - reserved
                          from room_inventory
                         where hotel_id = :hotelId
                           and room_id = :roomId
                           and stay_date >= :checkIn
                           and stay_date < :checkOut
                        """)
                        .setParameter(HOTEL_ID, hotelId)
                        .setParameter(ROOM_ID, roomId)
                        .setParameter("checkIn", checkIn)
                        .setParameter("checkOut", checkOut)
                        .getResultList();

        final var available = new HashMap<LocalDate, Integer>();
        for (final var row : rows) {
            available.put((LocalDate) row[0], ((Number) row[1]).intValue());
        }
        return available;
    }

    /** Limpeza entre testes: o inventário é estado global do banco, não da reserva. */
    public void deleteAll() {
        this.entityManager.createNativeQuery("delete from room_inventory").executeUpdate();
    }

    /** Usado nos testes para inspecionar o estado do inventário. */
    public int reserved(final Integer hotelId, final Integer roomId, final LocalDate night) {
        @SuppressWarnings("unchecked")
        final List<Number> rows = this.entityManager.createNativeQuery("""
                        select reserved from room_inventory
                         where hotel_id = :hotelId and room_id = :roomId and stay_date = :stayDate
                        """)
                        .setParameter(HOTEL_ID, hotelId)
                        .setParameter(ROOM_ID, roomId)
                        .setParameter(STAY_DATE, night)
                        .getResultList();
        return rows.isEmpty() ? 0 : rows.getFirst().intValue();
    }
}

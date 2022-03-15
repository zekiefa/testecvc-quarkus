package br.com.cvc.evaluation.service.mapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import br.com.cvc.evaluation.broker.dto.BrokerHotelRoom;
import br.com.cvc.evaluation.domain.PriceDetail;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class RoomMapperTest {
    private final RoomMapper mapper = Mappers.getMapper(RoomMapper.class);

    private final EasyRandom easyRandom = new EasyRandom();

    @Test
    void testToDomain() {
        final var brokerHotelRoom = easyRandom.nextObject(BrokerHotelRoom.class);

        final var room = this.mapper.toDomain(brokerHotelRoom);

        assertAll("domain",
                        () -> assertThat(room.getRoomID(), is(brokerHotelRoom.getRoomID())),
                        () -> assertThat(room.getCategoryName(), is(brokerHotelRoom.getCategoryName())),
                        () -> assertThat(room.getPriceDetail(), is(PriceDetail.builder()
                                        .pricePerDayAdult(BigDecimal.ZERO)
                                        .pricePerDayChild(BigDecimal.ZERO).build())),
                        () -> assertThat(room.getTotalPrice(), is(BigDecimal.ZERO))
        );
    }

    @Test
    void testNullToDomain() {
        final var room = this.mapper.toDomain(null);

        assertNull(room);
    }
}


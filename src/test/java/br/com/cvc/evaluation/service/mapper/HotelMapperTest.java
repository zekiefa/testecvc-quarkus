package br.com.cvc.evaluation.service.mapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class HotelMapperTest {
    private final HotelMapper mapper = Mappers.getMapper(HotelMapper.class);

    private final EasyRandom easyRandom = new EasyRandom();

    @Test
    void testToDomain() {
        final var brokerHotel = easyRandom.nextObject(BrokerHotel.class);

        final var hotel = this.mapper.toDomain(brokerHotel);

        assertAll("domain",
                        () -> assertThat(hotel.getId(), is(brokerHotel.getId())),
                        () -> assertThat(hotel.getCityName(), is(brokerHotel.getCityName())),
                        () -> assertFalse(hotel.getRooms().isEmpty())
        );
    }

    @Test
    void testNullToDomain() {
        final var hotel = this.mapper.toDomain(null);

        assertNull(hotel);
    }
}


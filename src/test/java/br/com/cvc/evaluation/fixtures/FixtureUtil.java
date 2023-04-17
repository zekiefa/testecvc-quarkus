package br.com.cvc.evaluation.fixtures;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import br.com.cvc.evaluation.broker.dto.BrokerHotelRoom;
import br.com.cvc.evaluation.broker.dto.Price;
import br.com.cvc.evaluation.domain.User;
import org.jeasy.random.EasyRandom;

public final class FixtureUtil {
    private static final EasyRandom EASY_RANDOM = new EasyRandom();

    private FixtureUtil() { }

    public static String nextString() {
        return EASY_RANDOM.nextObject(String.class);
    }

    public static String nextUUID() {
        return UUID.randomUUID().toString();
    }

    public static Integer nextInt() {
        return EASY_RANDOM.nextInt();
    }

    public static BrokerHotel nextBrokerHotelWithoutRooms() {
        return new BrokerHotel(nextInt(),
                        nextString(),
                        nextInt(),
                        nextString(),
                        null);
    }

    public static BrokerHotel nextBrokerHotelWithEmptyRooms() {
        return new BrokerHotel(nextInt(),
                        nextString(),
                        nextInt(),
                        nextString(),
                        Collections.emptyList());
    }

    public static BrokerHotel nextBrokerHotel() {
        return new BrokerHotel(nextInt(),
                        nextString(),
                        nextInt(),
                        nextString(),
                        List.of(nextBrokerHotelRoom()));
    }

    public static BrokerHotelRoom nextBrokerHotelRoom() {
        return new BrokerHotelRoom(nextInt(),
                        nextString(),
                        new Price(EASY_RANDOM.nextObject(BigDecimal.class),
                                        EASY_RANDOM.nextObject(BigDecimal.class)));
    }

    public static User nextUser() {
        return new User(nextString(), nextUUID(), Collections.emptySet());
    }

}

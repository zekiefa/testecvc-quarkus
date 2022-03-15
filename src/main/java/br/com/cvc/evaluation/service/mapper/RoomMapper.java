package br.com.cvc.evaluation.service.mapper;

import java.math.BigDecimal;

import br.com.cvc.evaluation.broker.dto.BrokerHotelRoom;
import br.com.cvc.evaluation.domain.PriceDetail;
import br.com.cvc.evaluation.domain.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi",
                imports = {BigDecimal.class, PriceDetail.class} )
public interface RoomMapper {
    @Mapping(target = "totalPrice", expression = "java(BigDecimal.ZERO)")
    @Mapping(target = "priceDetail", expression = "java(PriceDetail.builder().pricePerDayAdult(BigDecimal.ZERO).pricePerDayChild(BigDecimal.ZERO).build())")
    Room toDomain(final BrokerHotelRoom broker);
}

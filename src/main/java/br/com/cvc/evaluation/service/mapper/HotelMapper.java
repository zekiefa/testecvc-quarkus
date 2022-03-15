package br.com.cvc.evaluation.service.mapper;

import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import br.com.cvc.evaluation.domain.Hotel;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface HotelMapper {
    Hotel toDomain(final BrokerHotel broker);
}

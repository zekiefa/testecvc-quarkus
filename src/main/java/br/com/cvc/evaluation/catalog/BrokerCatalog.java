package br.com.cvc.evaluation.catalog;

import java.util.Objects;

import br.com.cvc.evaluation.broker.BrokerService;
import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import br.com.cvc.evaluation.broker.dto.BrokerHotelRoom;
import br.com.cvc.evaluation.exceptions.BrokerUnavailableException;
import br.com.cvc.evaluation.exceptions.HotelNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Porta de acesso ao catálogo do broker (ADR-002): traduz falhas de transporte em
 * respostas do nosso domínio, para que nenhum outro serviço precise conhecer
 * {@code WebApplicationException}.
 */
@ApplicationScoped
public class BrokerCatalog {

    private final BrokerService broker;

    public BrokerCatalog(@RestClient final BrokerService broker) {
        this.broker = broker;
    }

    public BrokerHotel hotel(final Integer hotelId) {
        try {
            final var hotel = this.broker.getHotelDetails(hotelId);
            if (hotel == null) {
                throw new HotelNotFoundException("Hotel not found: " + hotelId);
            }
            return hotel;
        } catch (final WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new HotelNotFoundException("Hotel not found: " + hotelId);
            }
            throw new BrokerUnavailableException("Broker failed: " + e.getMessage());
        } catch (final ProcessingException e) {
            throw new BrokerUnavailableException("Broker unreachable: " + e.getMessage());
        }
    }

    public BrokerHotelRoom room(final Integer hotelId, final Integer roomId) {
        final var hotel = this.hotel(hotelId);
        if (hotel.rooms() == null) {
            throw new HotelNotFoundException("Hotel has no rooms: " + hotelId);
        }
        return hotel.rooms().stream()
                        .filter(room -> Objects.equals(room.roomID(), roomId))
                        .findFirst()
                        .orElseThrow(() -> new HotelNotFoundException(
                                        "Room " + roomId + " not found in hotel " + hotelId));
    }
}

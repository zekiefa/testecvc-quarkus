package br.com.cvc.evaluation.broker;

import java.time.LocalDate;

import br.com.cvc.evaluation.broker.dto.BrokerHotelDetail;
import br.com.cvc.evaluation.broker.dto.BrokerQuote;
import br.com.cvc.evaluation.broker.dto.BrokerQuoteRequest;
import br.com.cvc.evaluation.broker.dto.BrokerSearchResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Cliente do contrato v1 do broker (repositório separado: testecvc-broker-quarkus).
 *
 */
@Path("/")
@RegisterRestClient(configKey = "broker-v1")
public interface BrokerQuoteClient {

    @POST
    @Path("v1/quotes")
    BrokerQuote quote(BrokerQuoteRequest request);

    /** Catálogo do hotel: quartos e regimes, sem preço (o preço vem da cotação). */
    @GET
    @Path("v1/hotels/{hotelId}")
    BrokerHotelDetail hotel(@PathParam("hotelId") long hotelId);

    /** Busca por destino: um item por hotel, com as noites do plano mais barato. */
    @GET
    @Path("v1/search")
    BrokerSearchResponse search(@QueryParam("cityCode") String cityCode,
                    @QueryParam("checkIn") LocalDate checkIn,
                    @QueryParam("checkOut") LocalDate checkOut,
                    @QueryParam("adults") int adults,
                    @QueryParam("children") int children);
}

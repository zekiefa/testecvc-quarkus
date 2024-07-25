package br.com.cvc.evaluation.broker;

import java.util.Set;

import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@ApplicationScoped
@RegisterRestClient
public interface BrokerService {
    @GET
    @Path("cities/{codeCity}/hotels")
    Set<BrokerHotel> findHotelsByCity(@PathParam("codeCity") final Integer codeCity);

    @GET
    @Path("hotels/{codeHotel}")
    BrokerHotel getHotelDetails(@PathParam("codeHotel") final Integer codeHotel);
}

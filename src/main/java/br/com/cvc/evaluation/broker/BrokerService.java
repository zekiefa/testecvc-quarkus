package br.com.cvc.evaluation.broker;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.util.Set;

import br.com.cvc.evaluation.broker.dto.BrokerHotel;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/hotels")
@ApplicationScoped
@RegisterRestClient
public interface BrokerService {
    @GET
    @Path("/avail/{codeCity}")
    Set<BrokerHotel> findHotelsByCity(@PathParam("codeCity") final Integer codeCity);

    @GET
    @Path("/{codeHotel}")
    BrokerHotel getHotelDetails(@PathParam("codeHotel") final Integer codeHotel);
}

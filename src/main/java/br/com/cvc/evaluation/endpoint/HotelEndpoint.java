package br.com.cvc.evaluation.endpoint;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import br.com.cvc.evaluation.domain.Hotel;
import br.com.cvc.evaluation.service.BookingService;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@Path("/hotels")
public class HotelEndpoint {
    @Inject
    BookingService service;

    @GET
    @Path("/{id}")
    public Hotel find(@PathParam("id") final Integer id) {
        return service.getHotelDetails(id).orElse(Hotel.builder().build());
    }

}
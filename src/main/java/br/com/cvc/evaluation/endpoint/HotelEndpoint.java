package br.com.cvc.evaluation.endpoint;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import br.com.cvc.evaluation.service.BookingService;

@Path("/hotels")
public class HotelEndpoint {
    @Inject
    BookingService service;

    @GET
    @RolesAllowed({"user"})
    @Path("/{id}")
    public Response find(@PathParam("id") final Integer id) {
        final var result = service.getHotelDetails(id);

        if (result.isEmpty())
            return Response.noContent().build();

        return Response.ok(result.get()).build();
    }

}
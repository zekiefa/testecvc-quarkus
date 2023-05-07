package br.com.cvc.evaluation.endpoint;

import br.com.cvc.evaluation.service.BookingService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/hotels")
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
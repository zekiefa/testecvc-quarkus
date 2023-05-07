package br.com.cvc.evaluation.endpoint;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import br.com.cvc.evaluation.service.BookingService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/booking")
public class BookingEndpoint {
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final BookingService service;

    public BookingEndpoint(final BookingService service) {
        this.service = service;
    }

    @GET
    @RolesAllowed({"user"})
    @Path("/{cityCode}/{checkin}/{checkout}/{adults}/{children}")
    public Response find(@PathParam("cityCode") final Integer cityCode,
                    @PathParam("checkin") final String checkin,
                    @PathParam("checkout") final String checkout,
                    @PathParam("adults") final Integer adults,
                    @PathParam("children") final Integer children) {

        return Response
                        .ok(service.findHotels(cityCode, this.parseDate(checkin), this.parseDate(checkout),
                                        adults, children))
                        .build();
    }

    private LocalDate parseDate(final String date) {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_FORMAT));
    }
}

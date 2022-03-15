package br.com.cvc.evaluation.endpoint;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.cvc.evaluation.domain.Hotel;
import br.com.cvc.evaluation.service.BookingService;

@Path("/booking")
public class BookingEndpoint {
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    @Inject
    BookingService service;

    @GET
    @Path("/{cityCode}/{checkin}/{checkout}/{adults}/{children}")
    public List<Hotel> find(@PathParam("cityCode") final Integer cityCode,
                    @PathParam("checkin") final String checkin,
                    @PathParam("checkout") final String checkout,
                    @PathParam("adults") final Integer adults,
                    @PathParam("children") final Integer children) {

        return service.findHotels(cityCode, this.parseDate(checkin), this.parseDate(checkout),
                        adults, children);
    }

    private LocalDate parseDate(final String date) {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_FORMAT));
    }
}

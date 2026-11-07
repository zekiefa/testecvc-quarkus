package br.com.cvc.evaluation.endpoint;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import br.com.cvc.evaluation.service.BookingService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Detalhe do hotel <b>para um período</b>.
 *
 * <p>Preço é por data de estadia, então período é obrigatório: sem ele não existe preço
 * verdadeiro, e devolver um "de referência" seria inventar. Quem quer só o catálogo usa
 * {@code GET /api/v1/booking/{cityCode}/...}, que já devolve o melhor plano por hotel.</p>
 */
@Path("/api/v1/hotels")
public class HotelEndpoint {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final BookingService service;

    public HotelEndpoint(final BookingService service) {
        this.service = service;
    }

    @GET
    @RolesAllowed({"user"})
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Object find(@PathParam("id") final Integer id,
                    @QueryParam("checkIn") final String checkIn,
                    @QueryParam("checkOut") final String checkOut,
                    @QueryParam("adults") final Integer adults,
                    @QueryParam("children") final Integer children) {
        if (id == null || id < 1) {
            throw new InvalidRequestException("id must be a positive integer: " + id);
        }

        return this.service.getHotelDetails(id, parseDate("checkIn", checkIn), parseDate("checkOut", checkOut),
                        adults == null ? 1 : adults, children == null ? 0 : children);
    }

    private LocalDate parseDate(final String parameter, final String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(parameter + " is required (yyyy-MM-dd)");
        }
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (final DateTimeParseException _) {
            throw new InvalidRequestException(parameter + " must be an ISO-8601 date (yyyy-MM-dd): " + value);
        }
    }
}

package br.com.cvc.evaluation.inventory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import br.com.cvc.evaluation.service.provider.BookingSettings;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/hotels")
public class AvailabilityResource {
    private final InventoryService inventory;
    private final BookingSettings settings;

    public AvailabilityResource(final InventoryService inventory, final BookingSettings settings) {
        this.inventory = inventory;
        this.settings = settings;
    }

    @GET
    @Path("/{id}/availability")
    @RolesAllowed("user")
    public Response availability(@PathParam("id") final Integer id,
                    @QueryParam("checkIn") final String checkIn,
                    @QueryParam("checkOut") final String checkOut) {

        final var from = parseDate("checkIn", checkIn);
        final var to = parseDate("checkOut", checkOut);
        if (!to.isAfter(from)) {
            throw new InvalidRequestException("checkOut must be after checkIn");
        }
        // O limite de noites é regra de negócio: sem esta checagem o período longo
        // estoura IllegalArgument dentro do serviço e vira 500.
        if (from.until(to, java.time.temporal.ChronoUnit.DAYS) > this.settings.maxNights()) {
            throw new InvalidRequestException(
                            "Period must not exceed " + this.settings.maxNights() + " nights");
        }

        return Response.ok(this.inventory.availability(id, from, to)).build();
    }

    private LocalDate parseDate(final String parameter, final String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(parameter + " is required (yyyy-MM-dd)");
        }
        try {
            return LocalDate.parse(value);
        } catch (final DateTimeParseException e) {
            throw new InvalidRequestException(parameter + " must be an ISO-8601 date (yyyy-MM-dd): " + value);
        }
    }
}

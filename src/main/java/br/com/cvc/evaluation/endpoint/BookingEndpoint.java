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
import jakarta.ws.rs.core.Response;

@Path("/api/v1/booking")
public class BookingEndpoint {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    /**
     * Slug de cidade aceito: minúsculas, dígitos e hífen.
     *
     * <p>Além de recusar entrada sem sentido, isso corta o caractere de controle que
     * permitiria forjar linha de log ({@code %0d%0a}) — o valor vem de path param.</p>
     */
    private static final java.util.regex.Pattern CITY_CODE = java.util.regex.Pattern.compile("[a-z0-9-]{1,64}");

    private final BookingService service;

    public BookingEndpoint(final BookingService service) {
        this.service = service;
    }

    @GET
    @RolesAllowed({"user"})
    @Path("/{cityCode}/{checkin}/{checkout}/{adults}/{children}")
    public Response find(@PathParam("cityCode") final String cityCode,
                    @PathParam("checkin") final String checkin,
                    @PathParam("checkout") final String checkout,
                    @PathParam("adults") final Integer adults,
                    @PathParam("children") final Integer children) {

        if (cityCode == null || !CITY_CODE.matcher(cityCode).matches()) {
            throw new InvalidRequestException(
                            "cityCode must match [a-z0-9-]{1,64} (ex.: serra-negra): " + cityCode);
        }

        return Response
                        .ok(service.findHotels(cityCode,
                                        this.parseDate("checkin", checkin),
                                        this.parseDate("checkout", checkout),
                                        adults,
                                        children))
                        .build();
    }

    /**
     * Converte uma data ISO-8601 (yyyy-MM-dd). Entrada inválida vira 400 com a mensagem
     * do parâmetro — antes, um {@code LocalDate.parse} sem tratamento devolvia 500.
     */
    private LocalDate parseDate(final String parameter, final String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(parameter + " is required (yyyy-MM-dd)");
        }

        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (final DateTimeParseException e) {
            throw new InvalidRequestException(parameter + " must be an ISO-8601 date (yyyy-MM-dd): " + value);
        }
    }
}

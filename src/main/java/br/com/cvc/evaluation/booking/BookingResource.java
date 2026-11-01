package br.com.cvc.evaluation.booking;

import java.net.URI;

import br.com.cvc.evaluation.booking.dto.BookingResponse;
import br.com.cvc.evaluation.booking.dto.CreateBookingRequest;
import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Criação de reserva. O dono é o {@code sub} do JWT — nunca vem do corpo, para que o
 * cliente não possa reservar em nome de outro.
 */
@Path("/api/v1/bookings")
public class BookingResource {
    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final BookingCreationService service;

    /** Injetado (não é parâmetro): no JAX-RS, parâmetro sem anotação vira body. */
    @Inject
    JsonWebToken jwt;

    public BookingResource(final BookingCreationService service) {
        this.service = service;
    }

    @POST
    @RolesAllowed("user")
    public Response create(@HeaderParam(IDEMPOTENCY_HEADER) final String idempotencyKey,
                    @Valid final CreateBookingRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException(IDEMPOTENCY_HEADER + " header is required");
        }

        final var result = this.service.create(request, idempotencyKey, jwt.getSubject());
        final var body = BookingResponse.from(result.booking());

        if (result.created()) {
            return Response.created(URI.create("/api/v1/bookings/" + body.id())).entity(body).build();
        }
        // Replay idempotente: mesma resposta, sem efeito colateral.
        return Response.ok(body).build();
    }
}

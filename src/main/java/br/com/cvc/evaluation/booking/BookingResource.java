package br.com.cvc.evaluation.booking;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import br.com.cvc.evaluation.booking.dto.BookingResponse;
import br.com.cvc.evaluation.booking.dto.CreateBookingRequest;
import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
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
    private final BookingQueryService queryService;
    private final BookingLifecycleService lifecycleService;

    /** Injetado (não é parâmetro): no JAX-RS, parâmetro sem anotação vira body. */
    @Inject
    JsonWebToken jwt;

    public BookingResource(final BookingCreationService service, final BookingQueryService queryService,
                    final BookingLifecycleService lifecycleService) {
        this.service = service;
        this.queryService = queryService;
        this.lifecycleService = lifecycleService;
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

    @GET
    @Path("/{id}")
    @RolesAllowed("user")
    public Response get(@PathParam("id") final UUID id) {
        final var booking = this.queryService.getOwned(id, this.jwt.getSubject());
        return Response.ok(BookingResponse.from(booking)).build();
    }

    @POST
    @Path("/{id}/cancel")
    @RolesAllowed("user")
    public Response cancel(@PathParam("id") final UUID id) {
        final var booking = this.queryService.cancel(id, this.jwt.getSubject());
        return Response.ok(BookingResponse.from(booking)).build();
    }

    @POST
    @Path("/{id}/check-in")
    @RolesAllowed("user")
    public Response checkIn(@PathParam("id") final UUID id) {
        final var booking = this.lifecycleService.checkIn(id, this.jwt.getSubject(), LocalDate.now());
        return Response.ok(BookingResponse.from(booking)).build();
    }

    @POST
    @Path("/{id}/check-out")
    @RolesAllowed("user")
    public Response checkOut(@PathParam("id") final UUID id) {
        final var booking = this.lifecycleService.checkOut(id, this.jwt.getSubject(), LocalDate.now());
        return Response.ok(BookingResponse.from(booking)).build();
    }

    @POST
    @Path("/{id}/no-show")
    @RolesAllowed("user")
    public Response noShow(@PathParam("id") final UUID id) {
        final var booking = this.lifecycleService.noShow(id, this.jwt.getSubject(), LocalDate.now());
        return Response.ok(BookingResponse.from(booking)).build();
    }
}

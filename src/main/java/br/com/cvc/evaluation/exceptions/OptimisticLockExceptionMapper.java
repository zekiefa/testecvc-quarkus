package br.com.cvc.evaluation.exceptions;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Duas requisições tentaram mudar a mesma reserva ao mesmo tempo: a segunda perdeu a
 * versão. É conflito recuperável pelo cliente (recarregar e decidir), então 409 — nunca 500.
 */
@Provider
public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(final OptimisticLockException exception) {
        return ProblemDetail.typed("concurrent-modification",
                        Response.Status.CONFLICT.getStatusCode(),
                        "Booking was modified concurrently; reload and retry",
                        this.uriInfo == null ? null : this.uriInfo.getRequestUri())
                        .toResponse();
    }
}

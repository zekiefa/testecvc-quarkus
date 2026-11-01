package br.com.cvc.evaluation.exceptions;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Duas requisições tentaram mudar a mesma reserva ao mesmo tempo: a segunda perdeu a
 * versão. É conflito recuperável pelo cliente (recarregar e decidir), então 409 — nunca 500.
 */
@Provider
public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {

    @Override
    public Response toResponse(final OptimisticLockException exception) {
        return Response.status(Response.Status.CONFLICT)
                        .entity("Booking was modified concurrently; reload and retry")
                        .build();
    }
}

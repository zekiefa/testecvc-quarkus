package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Exceções de domínio → RFC 7807, com {@code type} estável por classe de problema. */
@Provider
public class EvaluationExceptionHandler implements ExceptionMapper<CustomException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(final CustomException exception) {
        final var problem = ProblemDetail.typed(ProblemSlugs.of(exception.getClass()),
                        exception.getStatus(),
                        exception.getMessage(),
                        this.uriInfo == null ? null : this.uriInfo.getRequestUri());
        return problem.toResponse();
    }
}

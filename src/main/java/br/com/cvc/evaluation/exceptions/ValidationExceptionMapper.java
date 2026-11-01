package br.com.cvc.evaluation.exceptions;

import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Falha de validação de entrada (corpo ou parâmetros) → 400 em RFC 7807, listando os
 * campos inválidos no {@code detail}. Antes disso, o cliente recebia um HTML/texto padrão
 * do framework e não sabia qual campo corrigir.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(final ConstraintViolationException exception) {
        final var detail = exception.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .collect(Collectors.joining("; "));

        final var problem = ProblemDetail.typed("validation-failed",
                        Response.Status.BAD_REQUEST.getStatusCode(),
                        detail.isBlank() ? "Request validation failed" : detail,
                        this.uriInfo == null ? null : this.uriInfo.getRequestUri());
        return problem.toResponse();
    }
}

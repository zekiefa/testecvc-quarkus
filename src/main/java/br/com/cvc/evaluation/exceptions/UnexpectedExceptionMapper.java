package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rede de segurança: qualquer exceção não mapeada vira 500 em RFC 7807 <b>sem</b> expor
 * stack trace ou mensagem interna. O que o cliente precisa é o status; o diagnóstico
 * fica no log, com a exceção completa.
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = LoggerFactory.getLogger(UnexpectedExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(final Throwable exception) {
        log.error("Unhandled exception while serving the request", exception);
        return ProblemDetail.of(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        "Unexpected error; the incident was logged",
                        this.uriInfo == null ? null : this.uriInfo.getRequestUri())
                        .toResponse();
    }
}

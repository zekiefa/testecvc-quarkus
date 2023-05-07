package br.com.cvc.evaluation.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class EvaluationExceptionHandler implements ExceptionMapper<CustomException> {

    @Override
    public Response toResponse(final CustomException e) {
        return Response
                        .status(e.getStatus())
                        .entity(e.getMessage())
                        .build();
    }
}

package br.com.cvc.evaluation.exceptions;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

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

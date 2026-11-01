package br.com.cvc.evaluation.exceptions;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.ws.rs.core.Response;
import org.slf4j.MDC;

/**
 * Corpo de erro no formato RFC 7807 ({@code application/problem+json}).
 *
 * <p>Campos: {@code type} identifica a <b>classe</b> do problema (estável, para o cliente
 * programar contra), {@code title} é o resumo legível, {@code status} repete o HTTP status,
 * {@code detail} explica esta ocorrência e {@code instance} aponta a requisição.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(URI type, String title, int status, String detail, URI instance,
                String traceId) {

    static final String MEDIA_TYPE = "application/problem+json";
    static final String PROBLEM_TYPE_BASE = "https://testecvc.example/problems/";

    public static ProblemDetail of(final int status, final String detail, final URI instance) {
        return new ProblemDetail(URI.create("about:blank"), reasonOf(status), status, detail, instance,
                        currentTraceId());
    }

    public static ProblemDetail typed(final String slug, final int status, final String detail, final URI instance) {
        return new ProblemDetail(URI.create(PROBLEM_TYPE_BASE + slug), reasonOf(status), status, detail, instance,
                        currentTraceId());
    }

    /** Constrói a resposta já com o media type correto — sem isso, viram "mais um JSON". */
    public Response toResponse() {
        return Response.status(status).type(MEDIA_TYPE).entity(this).build();
    }

    /** O mesmo id que o filtro colocou no MDC — é o elo entre o erro e a linha de log. */
    private static String currentTraceId() {
        return MDC.get("traceId");
    }

    private static String reasonOf(final int status) {
        final var known = Response.Status.fromStatusCode(status);
        return known == null ? "HTTP " + status : known.getReasonPhrase();
    }
}

package br.com.cvc.evaluation.endpoint;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.com.cvc.evaluation.domain.Login;
import br.com.cvc.evaluation.domain.Profile;
import br.com.cvc.evaluation.domain.Token;
import br.com.cvc.evaluation.service.TokenService;
import br.com.cvc.evaluation.service.UserService;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@RequestScoped
public class AuthenticationEndpoint {
    private final TokenService tokenService;
    private final UserService userService;

    public AuthenticationEndpoint(final TokenService tokenService,
                    final UserService userService) {
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @POST
    public Response login(final Login loginAuth) {
        final var search = this.userService.findByLogin(loginAuth.user());

        if (search.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(),
                                            "User not found")
                            .build();
        }

        final var user = search.get();
        if (!BcryptUtil.matches(loginAuth.passwd(), user.password()))
            return Response.status(Response.Status.FORBIDDEN.getStatusCode(),
                                            "User not authorized")
                            .build();

        final var token = tokenService.generateToken(user.username(),
                        Stream.ofNullable(user.profiles())
                                        .flatMap(Set::stream)
                                        .map(Profile::name)
                                        .collect(Collectors.toSet()));
        final var response = new Token(token, "Bearer");

        return Response.ok(response).build();
    }
}

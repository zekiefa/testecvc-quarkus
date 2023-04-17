package br.com.cvc.evaluation.endpoint;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.com.cvc.evaluation.domain.Login;
import br.com.cvc.evaluation.domain.Profile;
import br.com.cvc.evaluation.domain.Token;
import br.com.cvc.evaluation.service.TokenService;
import br.com.cvc.evaluation.service.UserService;
import io.quarkus.elytron.security.common.BcryptUtil;

@Path("/auth")
@RequestScoped
public class AuthenticationEndpoint {
    @Inject
    TokenService tokenService;
    @Inject
    UserService userService;

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

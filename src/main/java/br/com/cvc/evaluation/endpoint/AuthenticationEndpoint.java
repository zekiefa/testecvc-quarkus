package br.com.cvc.evaluation.endpoint;

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
public class AuthenticationEndpoint {
    @Inject
    TokenService tokenService;
    @Inject
    UserService userService;

    @POST
    public Response login(final Login loginAuth) {
        final var search = this.userService.findByLogin(loginAuth.getUser());

        if (search.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND.getStatusCode(),
                                            "User not found")
                            .build();
        }

        final var user = search.get();
        if (!BcryptUtil.matches(loginAuth.getPasswd(), user.getPassword()))
            return Response.status(Response.Status.FORBIDDEN.getStatusCode(),
                                            "User not authorized")
                            .build();

        final var token = tokenService.generateToken(user.getUsername(),
                        Stream.ofNullable(user.getProfiles())
                                        .flatMap(Set::stream)
                                        .map(Profile::getName)
                                        .collect(Collectors.toSet()));
        final var response = Token.builder()
                        .token(token)
                        .type("Bearer")
                        .build();

        return Response.ok(response).build();
    }
}

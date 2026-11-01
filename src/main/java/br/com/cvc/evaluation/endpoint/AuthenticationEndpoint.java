package br.com.cvc.evaluation.endpoint;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.com.cvc.evaluation.domain.Login;
import br.com.cvc.evaluation.domain.Profile;
import br.com.cvc.evaluation.domain.Token;
import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import br.com.cvc.evaluation.service.TokenService;
import br.com.cvc.evaluation.service.UserService;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@RequestScoped
public class AuthenticationEndpoint {
    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    /**
     * Hash descartável: quando o usuário não existe, gastamos o mesmo tempo de bcrypt
     * de quando ele existe, para não vazar a existência do usuário pelo tempo de resposta.
     */
    private static final String DUMMY_HASH = BcryptUtil.bcryptHash("dummy-password-for-timing");

    private final TokenService tokenService;
    private final UserService userService;

    public AuthenticationEndpoint(final TokenService tokenService,
                    final UserService userService) {
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @POST
    public Response login(final Login loginAuth) {
        if (loginAuth == null || !hasText(loginAuth.user()) || !hasText(loginAuth.passwd())) {
            throw new InvalidRequestException("user and passwd are required");
        }

        final var search = this.userService.findByLogin(loginAuth.user());
        final var authenticated = search
                        .map(user -> BcryptUtil.matches(loginAuth.passwd(), user.password()))
                        .orElseGet(() -> {
                            BcryptUtil.matches(loginAuth.passwd(), DUMMY_HASH);
                            return false;
                        });

        if (!authenticated) {
            // 401 genérico: não distingue "usuário inexistente" de "senha errada".
            return Response.status(Response.Status.UNAUTHORIZED)
                            .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                            .entity(INVALID_CREDENTIALS)
                            .build();
        }

        final var user = search.orElseThrow();
        final var token = tokenService.generateToken(user.username(),
                        Stream.ofNullable(user.profiles())
                                        .flatMap(Set::stream)
                                        .map(Profile::name)
                                        .collect(Collectors.toSet()));

        // Token(type, token) — a ordem dos campos do record importa.
        return Response.ok(new Token("Bearer", token)).build();
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}

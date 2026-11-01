# Simple Quarkus Project

### Architecture
- Java ~~17~~ 21
- Quarkus ~~3.0.1.Final~~ 3.12
- ~~[Lombok](https://projectlombok.org) to avoid boilerplate code~~
- ~~[Mapstruct](https://mapstruct.org) for conversions between domains~~
- [SL4J](https://www.slf4j.org/manual.html) for logging
- [JUnit 5](https://junit.org/junit5/docs/current/user-guide) for unit tests
- [Mockito](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Hamcrest](http://hamcrest.org/JavaHamcrest) for alternative asserts
- [EasyRandom](https://github.com/j-easy/easy-random) for generate randomic objects
- [Wiremock](https://wiremock.org/docs/junit-jupiter) for integrations tests

### Instructions
- Clone the project
- Generate the local JWT key pair: `$ ./generate_jwtkeys.sh`
  - As chaves são segredos e **não estão no repositório** (`src/main/resources/jwt/` é ignorado).
  - Para usar outro diretório/secret manager: `JWT_SIGN_KEY_LOCATION` e `JWT_VERIFY_KEY_LOCATION`.
- Build the application `$ mvn clean install`
- Alternatively build the native executable `$ mvn install -Dnative`
- Run the project: `$ mvn quarkus:dev -DdebugHost=0.0.0.0`

### About the application
It's a RESTFull API for booking and searching a hotel with JWT authentication.

### JWT Authentication 
```shell
curl --request POST \
--url http://localhost:8080/auth \
--header 'Content-Type: application/json' \
--data '{
"user": "usuario",
"passwd": "senha"
}'
```

### Access the application
O token retornado pelo `/auth` expira (padrão: 24h), então guarde-o numa variável:

```shell
TOKEN=$(curl -s --request POST --url http://localhost:8080/auth \
  --header 'Content-Type: application/json' \
  --data '{"user":"usuario","passwd":"senha"}' | jq -r .token)

curl --request GET \
  --url http://localhost:8080/api/v1/hotels/6 \
  --header "Authorization: Bearer $TOKEN"
```

Busca de hotéis por cidade (datas em ISO-8601; `checkout` precisa ser depois de `checkin`):

```shell
curl --request GET \
  --url "http://localhost:8080/api/v1/booking/55/2026-11-10/2026-11-13/2/1" \
  --header "Authorization: Bearer $TOKEN"
```

Erros de entrada: `401` para credencial inválida (sem revelar se o usuário existe), `400` para
data fora do formato ISO-8601, período inválido ou contagem de hóspedes inválida.

### Documentação
- [Arquitetura (v0.1)](docs/arquitetura.md) — as-built, restrições, arquitetura-alvo e marcos M0–M4
- [Decisões de arquitetura (ADRs)](docs/adr/README.md) — índice das decisões vigentes

### References
- [Getting Started With Quarkus](https://www.section.io/engineering-education/how-to-work-with-lombok-on-quarkus)
- [Migration Guide 3.0](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.0)
- [Quarkus Guide: Using JWT RBAC](https://quarkus.io/guides/security-jwt#adding-a-public-key)
- [Quarkus Guide: Build, Sign and Encrypt JSON Web Tokens](https://quarkus.io/guides/security-jwt-build)
- [Authentication and Authorization using JWT Token and Roles-Based Access Control | Quarkus Tutorial](https://www.youtube.com/watch?v=0FVJlSJR0Zo&t=3s)


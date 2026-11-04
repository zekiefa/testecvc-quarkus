# API de Reservas de Hotel — Quarkus

> API REST em Quarkus que consulta catálogo e tarifas de um **broker externo**, mantém o
> **próprio inventário** e gerencia o ciclo de vida da reserva: criação idempotente, consulta,
> cancelamento, check-in, check-out e no-show.

| | |
|---|---|
| Java | 21 |
| Quarkus | 3.40.1.1 |
| Banco | PostgreSQL 16 via Dev Services (nada de H2) |
| Schema | Flyway (migrações `V1`–`V4`); o Hibernate nunca altera o schema |
| Autenticação | JWT RS256 (`usuario`/`senha` em desenvolvimento, bcrypt no banco) |
| Testes | **98** em 20 classes, com gate de cobertura |
| CI | GitHub Actions: build + testes, SonarQube Cloud, CodeQL, Dependabot |

## Requisitos

- **JDK 21** e **Maven 3.9+**
- **Docker em execução** — o PostgreSQL sobe sozinho via Dev Services, inclusive nos testes.
  Sem o daemon, a suíte **não roda** (veja [Testes](#testes)).

## Como rodar

```shell
# 1. Chaves JWT (segredos não ficam no repositório)
./generate_jwtkeys.sh

# 2. Subir em modo dev (o Postgres sobe via Dev Services)
mvn quarkus:dev -DdebugHost=0.0.0.0
```

Alternativa: `mvn clean install` para o jar, ou `mvn install -Dnative` para executável nativo.
Para usar outro diretório de chaves: `JWT_SIGN_KEY_LOCATION` e `JWT_VERIFY_KEY_LOCATION`.

## Autenticação

```shell
TOKEN=$(curl -s --request POST --url http://localhost:8080/auth \
  --header 'Content-Type: application/json' \
  --data '{"user":"usuario","passwd":"senha"}' | jq -r .token)
```

Resposta: `{"type":"Bearer","token":"..."}` — expira em 24h no modo dev.
Credencial inválida devolve **401 genérico**, sem revelar se o usuário existe.

## Endpoints

| Método | Caminho | Papel | Descrição |
|---|---|---|---|
| `POST` | `/auth` | público | Login; devolve o JWT |
| `GET` | `/api/v1/hotels/{id}` | `user` | Detalhe do hotel com preço por diária |
| `GET` | `/api/v1/hotels/{id}/availability?checkIn&checkOut` | `user` | Disponibilidade por noite e por quarto |
| `POST` | `/api/v1/bookings` | `user` | Cria reserva (exige `Idempotency-Key`) |
| `GET` | `/api/v1/bookings/{id}` | `user` (dono) | Consulta a reserva |
| `POST` | `/api/v1/bookings/{id}/cancel` | `user` (dono) | Cancela e devolve o estoque |
| `POST` | `/api/v1/bookings/{id}/check-in` | `user` (dono) | Check-in na data ou depois |
| `POST` | `/api/v1/bookings/{id}/check-out` | `user` (dono) | Exige `CHECKED_IN` |
| `POST` | `/api/v1/bookings/{id}/no-show` | `user` (dono) | Após a data, sem check-in |
| `GET` | `/api/v1/booking/{cityCode}/{checkIn}/{checkOut}/{adults}/{children}` | `user` | Busca por cidade (**legado**, ver [dívidas](docs/arquitetura.md)) |
| `GET` | `/q/openapi` · `/q/health` · `/q/metrics` | — | Contrato, saúde e métricas |

### Exemplo: disponibilidade

```shell
curl -s --request GET \
  --url "http://localhost:8080/api/v1/hotels/1/availability?checkIn=2027-04-10&checkOut=2027-04-13" \
  --header "Authorization: Bearer $TOKEN" | jq
```

`availableRooms` é o **mínimo entre as noites** — é quanto pode ser vendido para o período inteiro.
O detalhe por noite mostra qual data trava a venda.

### Exemplo: criar reserva

```shell
curl -s --request POST --url http://localhost:8080/api/v1/bookings \
  --header "Authorization: Bearer $TOKEN" \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: 7b1f0c2e-3a44-4c1e-9d2a-1f0e9c3b5a21' \
  --data '{"hotelId":1,"roomId":1,"checkIn":"2027-04-10","checkOut":"2027-04-13","adults":1,"children":1}' \
  -i | jq
```

Resposta: **201** com `Location`, ou **200** quando a mesma `Idempotency-Key` é repetida
(devolve a reserva original, sem criar outra).

## Regras que o serviço aplica

**Idempotência**
- Mesma `Idempotency-Key` do mesmo dono → `200` com a reserva original, sem efeito novo.
- Mesma chave de **outro** dono → `409` (não vaza dados de terceiros).
- Requisições simultâneas: uma ganha, as outras releem a vencedora em transação nova.

**Preço**
- Taxa de **5% (500 bps)** sobre a tarifa base do broker, incidindo sobre adulto **e** criança.
- Fórmula: `diária = round(tarifa × 1,05, 2, HALF_UP)`; `total = Σ diárias × noites`.
- O preço é **congelado na reserva** (`nightlyBreakdown`) — mudança de tarifa no broker não
  altera reserva confirmada.
- Decisão de produto registrada em [DEC-0001](.super-pm/decisions.pt-BR.md) e [ADR-004](docs/adr/ADR-004-semantica-da-taxa.md).

**Inventário e estados**
- O estoque é **nosso**: cada noite é reservada por `UPDATE` condicional na mesma transação.
  Sem estoque → `409`, nunca overbooking.
- Grafo de estados: `CONFIRMED → CHECKED_IN → CHECKED_OUT`, mais `CANCELLED` e `NO_SHOW`.
  Transição fora do grafo → `409`. `NO_SHOW` **não** devolve estoque; `CANCELLED` devolve.
- Reserva de terceiro responde **404** (não 403 — 403 confirmaria a existência do id).

## Erros (RFC 7807)

Todo erro é `application/problem+json`:

```json
{
  "type": "https://testecvc.example/problems/invalid-booking-state",
  "title": "Conflict",
  "status": 409,
  "detail": "Booking is already CANCELLED; allowed transitions: []",
  "instance": "/api/v1/bookings/…/cancel",
  "traceId": "a36413f5-6fd9-4d94-b8ec-26b0cae6796c"
}
```

O `type` é estável por classe de problema (`…/booking-period-invalid`, `…/invalid-booking-state`,
`…/broker-unavailable`, `…/validation-failed`, `…/booking-not-found`), então o cliente programa
contra o tipo — não contra a mensagem. Validação estrutural devolve `400` listando os campos.

## Correlação (`traceId`)

Cada requisição recebe um id que aparece em **três lugares**:

| Onde | Como |
|---|---|
| Header da resposta | `X-Request-Id` |
| Corpo do erro | `traceId` (extensão da RFC 7807) |
| Log | MDC, impresso após o nível: `… INFO  [<id>] … - mensagem` |

O cliente pode enviar `X-Request-Id` (token curto: `[A-Za-z0-9._-]{1,64}`); caso contrário um UUID
é gerado. Assim quem reporta um erro entrega o id e a linha de log é encontrável.

## Observabilidade

| Recurso | Endpoint |
|---|---|
| Contrato OpenAPI | `/q/openapi` |
| Saúde (inclui datasource) | `/q/health` |
| Métricas Prometheus | `/q/metrics` (`http_server_requests_*`) |

## Testes

```shell
mvn clean verify        # 98 testes + gate de cobertura (80% de linhas por pacote)
```

> **Precisa de Docker.** Sem o daemon, os 18 testes `@QuarkusTest` falham na inicialização e o
> surefire reporta `Could not load class` no *discovery* — sintoma enganoso, que parece bug da
> ferramenta. A causa real aparece em `target/surefire-reports/*.dumpstream`. Os dois testes
> puros (`PricingCalculatorTest`, `BookingStateMachineTest`) continuam passando sem Docker.

O relatório HTML/XML fica em `target/jacoco-report/`.

## Estrutura

| Pacote | Papel |
|---|---|
| `booking` | Entity, máquina de estados, casos de uso, `BookingResource` |
| `inventory` | Hold por noite, disponibilidade, SQL condicional |
| `catalog` | ACL do broker (`BrokerCatalog`) |
| `pricing` | `PricingCalculator` — **puro**, sem CDI/I/O |
| `identity` | `AppUser`, `UserRepository`, seeder de desenvolvimento |
| `web` | `RequestIdFilter` (MDC, header, `traceId`) |
| `exceptions` | `ProblemDetail` + mappers RFC 7807 |
| `endpoint` / `service` / `domain` / `broker` | Camada legada (busca, detalhe, autenticação) coexistindo com os módulos novos |

Detalhes e dívidas conhecidas: [`docs/arquitetura.md`](docs/arquitetura.md).

## CI (GitHub Actions)

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — três jobs: **Build** (`-DskipTests package`),
  **Testes** (`mvn test`, com Dev Services) e **SonarQube Cloud** (consome a cobertura do job de
  testes). Build e testes rodam em paralelo.
- [`.github/workflows/codeql.yml`](.github/workflows/codeql.yml) — análise de segurança em push
  para `main`, PRs e semanalmente.
- [`.github/dependabot.yml`](.github/dependabot.yml) — atualizações agrupadas (extensões Quarkus
  juntas, libs de teste juntas).

**SonarQube Cloud** (gratuito em repositório público) — em *Settings → Secrets and variables → Actions*:

| Nome | Tipo | Valor |
|---|---|---|
| `SONAR_TOKEN` | secret | token gerado em sonarcloud.io |
| `SONAR_PROJECT_KEY` | variable | ex.: `zekiefa_testecvc-quarkus` |
| `SONAR_ORGANIZATION` | variable | slug da organização no SonarCloud |

Sem `SONAR_TOKEN` o passo de análise é pulado e o build continua verde.

## Documentação

- [Arquitetura v0.3](docs/arquitetura.md) — as-built, restrições, módulos, dados, riscos
  (v0.2 e v0.1 em [`docs/archive/`](docs/archive/))
- [ADRs](docs/adr/README.md) — decisões vigentes (monólito modular, fonte da verdade,
  idempotência/estados, semântica da taxa)
- [Decisões de produto](.super-pm/decisions.pt-BR.md) — DEC-0001: taxa de 5%

## References

- [Getting Started With Quarkus](https://www.section.io/engineering-education/how-to-work-with-lombok-on-quarkus)
- [Migration Guide 3.0](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.0)
- [Quarkus Guide: Using JWT RBAC](https://quarkus.io/guides/security-jwt#adding-a-public-key)
- [Quarkus Guide: Build, Sign and Encrypt JSON Web Tokens](https://quarkus.io/guides/security-jwt-build)
- [Authentication and Authorization using JWT Token and Roles-Based Access Control | Quarkus Tutorial](https://www.youtube.com/watch?v=0FVJlSJR0Zo&t=3s)
- [RFC 7807 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc7807)

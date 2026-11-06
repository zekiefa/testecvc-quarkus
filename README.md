# API de Reservas de Hotel — Quarkus

> API REST em Quarkus que consulta catálogo e tarifas de um **broker externo**, mantém o
> **próprio inventário** e gerencia o ciclo de vida da reserva: criação idempotente, consulta,
> cancelamento, check-in, check-out e no-show.

| | |
|---|---|
| Java | **25** (LTS) |
| Quarkus | 3.40.1.1 |
| Banco | PostgreSQL 16 via Dev Services (nada de H2) |
| Schema | Flyway (migrações `V1`–`V4`); o Hibernate nunca altera o schema |
| Autenticação | JWT RS256 (`usuario`/`senha` em desenvolvimento, bcrypt no banco) |
| Testes | **98** em 20 classes, com gate de cobertura |
| CI | GitHub Actions: build + testes, SonarQube Cloud, CodeQL, Dependabot |

## Requisitos

- **JDK 25** (LTS) e **Maven 3.9+**
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

Todos exigem `Authorization: Bearer <token>`, exceto `/auth` e `/q/*`.
Os IDs correspondem a [`docs/ai/capabilities.md`](docs/ai/capabilities.md).

| ID | Método | Caminho | Entrada | Sucesso | Erros |
|---|---|---|---|---|---|
| `RES-01` | `POST` | `/auth` | `{user, passwd}` | `200` `{type, token}` | `401` credencial inválida (sem revelar se o usuário existe) |
| `RES-02` | `GET` | `/api/v1/booking/{cityCode}/{checkIn}/{checkOut}/{adults}/{children}` | path: slug da cidade, datas ISO, pax | `200` uma oferta **por hotel**: melhor plano, total com taxa, noites, `dataSource` | `400` data/período/pax · `503` broker fora |
| `RES-03` | `GET` | `/api/v1/hotels/{id}?checkIn&checkOut&adults&children` | path + 4 query params (período **obrigatório**) | `200` hotel com todos os regimes cotados (`available`), `dataSource` | `400` período ausente/inválido · `404` hotel · `503` broker |
| `RES-04` | `GET` | `/api/v1/hotels/{id}/availability?checkIn&checkOut` | path + 2 query params | `200` noites livres + mínimo do período (nosso estoque) | `400` · `404` · `503` |
| `RES-05` | `POST` | `/api/v1/bookings` | header `Idempotency-Key` + `{hotelId, roomId, ratePlanId, checkIn, checkOut, adults, children}` | **`201`** + `Location` · **`200`** quando a chave repete | `400` validação · `404` hotel/quarto/plano · `409` sem estoque, chave de outro dono, tarifa ausente (`rate-unavailable`) · `503` broker |
| `RES-06` | `GET` | `/api/v1/bookings/{id}` | path | `200` reserva | `404` inexistente **ou de outro dono** |
| `RES-07` | `POST` | `/api/v1/bookings/{id}/cancel` | path | `200` status `CANCELLED` (devolve estoque) | `409` transição inválida · `404` |
| `RES-08` | `POST` | `/api/v1/bookings/{id}/check-in` | path | `200` status `CHECKED_IN` (data de entrada ou depois) | `409` data/estado · `404` |
| `RES-09` | `POST` | `/api/v1/bookings/{id}/check-out` | path | `200` status `CHECKED_OUT` (exige `CHECKED_IN`) | `409` · `404` |
| `RES-10` | `POST` | `/api/v1/bookings/{id}/no-show` | path | `200` status `NO_SHOW` (após a data; **não** devolve estoque) | `409` · `404` |
| `RES-11` | `GET` | `/q/openapi` · `/q/health` · `/q/metrics` | — | `200` contrato, saúde, métricas Prometheus | — |

Corpo de erro: sempre `application/problem+json` com `type` estável, `status`, `detail` e `traceId` — ver [Erros](#erros-rfc-7807).

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
  --data '{"hotelId":1,"roomId":1,"ratePlanId":1,"checkIn":"2027-04-10","checkOut":"2027-04-13","adults":1,"children":1}' \
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
- A tarifa vem do **broker** por data de estadia (`POST /v1/quotes`); `ratePlanId` é obrigatório
  porque preço e política de cancelamento vivem no plano.
- Taxa de **11,76% (1176 bps)** sobre a tarifa líquida de atacado, incidindo sobre adulto **e** criança.
  O valor vem de medição: com comissão de 15% do site, ele deixa o preço ~5% abaixo do mercado
  (`delta = (1 − comissão) × (1 + taxa) − 1`) — ver [DEC-0002](.super-pm/decisions.pt-BR.md).
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

## Integração com o broker

A reserva consulta o contrato **v1** do broker ([testecvc-broker-quarkus](https://github.com/zekiefa/testecvc-broker-quarkus)):

```shell
# suba o broker (porta 8081), depois este serviço
BROKER_V1_URL=http://localhost:8081 mvn quarkus:dev
```

Quatro guardas de contrato, todas no caminho da ACL:

| Situação | Resposta |
|---|---|
| cotação que não cobre todas as noites | `503` |
| cotação em moeda ≠ `booking.currency` | `503` |
| cotação com `staleAfter` no passado | `503` — o broker marcou o dado como vencido |
| oferta de busca vencida | descartada da lista |

A resposta da nossa API também **declara a origem** do preço (`dataSource: SEED` ou `PUSH`), então a
propriedade "dado sintético não se disfarça de real" sobrevive ao salto entre os serviços.

> Busca e detalhe usam **só** o contrato v1. O cliente legado (`BROKER_LEGACY_URL`) foi removido
> junto com o fixture `hotels.json`.

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

- **[Capacidades para agentes de IA](docs/ai/capabilities.md)** — features de negócio, invariantes
  numeradas, fronteira de dados e dependência do broker, em formato compacto (leia isto antes
  de varrer o código)

- [Arquitetura v0.3](docs/arquitetura.md) — as-built, restrições, módulos, dados, riscos
  (v0.2 e v0.1 em [`docs/archive/`](docs/archive/))
- [ADRs](docs/adr/README.md) — decisões vigentes (monólito modular, fonte da verdade,
  idempotência/estados, semântica da taxa)
- [Decisões de produto](.super-pm/decisions.pt-BR.md) — DEC-0002: taxa de 11,76% (substitui a DEC-0001, de 5%)

## References

- [Getting Started With Quarkus](https://www.section.io/engineering-education/how-to-work-with-lombok-on-quarkus)
- [Migration Guide 3.0](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.0)
- [Quarkus Guide: Using JWT RBAC](https://quarkus.io/guides/security-jwt#adding-a-public-key)
- [Quarkus Guide: Build, Sign and Encrypt JSON Web Tokens](https://quarkus.io/guides/security-jwt-build)
- [Authentication and Authorization using JWT Token and Roles-Based Access Control | Quarkus Tutorial](https://www.youtube.com/watch?v=0FVJlSJR0Zo&t=3s)
- [RFC 7807 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc7807)

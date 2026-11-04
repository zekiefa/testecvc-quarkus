# Arquitetura — API de Reservas de Hotel (testecvc-quarkus)

| Campo | Valor |
|---|---|
| Versão | **v0.3** |
| Data | 2026-11-03 |
| Status | **M0, M1, M2 e M3 implementados e mergeados na `main`**; CI ativo; M4 (IA) pausado |
| Documentos relacionados | [ADR-001](adr/ADR-001-monolito-modular.md) · [ADR-002](adr/ADR-002-fonte-da-verdade.md) · [ADR-003](adr/ADR-003-idempotencia-e-maquina-de-estados.md) · [ADR-004](adr/ADR-004-semantica-da-taxa.md) |
| Decisões de produto | [.super-pm/decisions.pt-BR.md](../.super-pm/decisions.pt-BR.md) (DEC-0001: taxa de 5%) |
| Base analisada | commit da `main` após o merge do PR #25 (Quarkus 3.40.1.1, Java 21) |
| Versões anteriores | [archive/arquitetura-v0.1.md](archive/arquitetura-v0.1.md) · [archive/arquitetura-v0.2.md](archive/arquitetura-v0.2.md) |

## Histórico de versões

| Versão | Data | Mudança |
|---|---|---|
| v0.3 | 2026-11-03 | As-built real: M1–M3 entregues. Módulos `booking`/`inventory`/`catalog`/`identity`/`web`; migrações V1–V4; CI de 3 jobs; Problem Details; `traceId`; identidade em banco; taxa decidida em 5% (DEC-0001). Riscos reescritos. |
| v0.2 | 2026-10-28 | M0 concluído e verificado; `.gitattributes`, rotação de chaves, `PricingCalculator` em bps, validação, 401. |
| v0.1 | 2026-10-28 | Versão inicial: as-built do estado anterior, restrições, arquitetura-alvo em 5 marcos e 4 ADRs. |

---

## 1. Posicionamento do sistema

> **Uma frase:** API REST em Quarkus que vende e gerencia reservas de quartos de hotel — consulta catálogo e tarifas de um broker externo, mantém **o próprio inventário** e o ciclo de vida da reserva (criação, consulta, check-in, check-out, cancelamento).

| Dimensão | Definição |
|---|---|
| Quem usa | Cliente HTTP autenticado por JWT (role `user`). Futuramente um **agente de IA** como cliente de primeira classe, via contrato OpenAPI. |
| Como é acionado | Requisições HTTP síncronas (REST/JSON). Sem filas, sem jobs assíncronos nesta fase. |
| Natureza dos dados | **Volátil e externo:** catálogo e tarifas do broker (leitura). **Durável e nosso:** reservas, inventário, usuários, eventos de reserva (PostgreSQL). |
| Fronteira do sistema | Busca e detalhe de hotel, disponibilidade, criação/consulta/cancelamento de reserva, check-in/check-out, autenticação e autorização. |
| **Fora de escopo (hoje)** | Pagamento, reembolso, faturamento, notificações/e-mail, multi-tenant, integração com PMS real, cancelamento parcial de diárias, IA/LLM. |

### 1.1 O que existe hoje (as-built)

O que a v0.2 chamava de "alvo" está entregue. Estado verificado na `main`:

| Capacidade | Estado | Onde |
|---|---|---|
| Autenticação JWT (RS256, `user`) | ✅ implementado | [AuthenticationEndpoint.java](../src/main/java/br/com/cvc/evaluation/endpoint/AuthenticationEndpoint.java) |
| Usuários em banco (bcrypt) | ✅ V4 + seeder dev-only | [AppUser.java](../src/main/java/br/com/cvc/evaluation/identity/AppUser.java) · [UserSeeder.java](../src/main/java/br/com/cvc/evaluation/identity/UserSeeder.java) |
| Busca de hotéis por cidade (broker) | ✅ endpoint legado | [BookingEndpoint.java](../src/main/java/br/com/cvc/evaluation/endpoint/BookingEndpoint.java) |
| Detalhe de hotel com preço | ✅ endpoint legado | [HotelEndpoint.java](../src/main/java/br/com/cvc/evaluation/endpoint/HotelEndpoint.java) |
| Disponibilidade por noite | ✅ | [AvailabilityResource.java](../src/main/java/br/com/cvc/evaluation/inventory/AvailabilityResource.java) |
| Criar reserva idempotente | ✅ `POST /api/v1/bookings` | [BookingResource.java](../src/main/java/br/com/cvc/evaluation/booking/BookingResource.java) |
| Consultar / cancelar | ✅ dono apenas, 404 para terceiros | [BookingQueryService.java](../src/main/java/br/com/cvc/evaluation/booking/BookingQueryService.java) |
| Check-in / check-out / no-show | ✅ com regras de calendário | [BookingLifecycleService.java](../src/main/java/br/com/cvc/evaluation/booking/BookingLifecycleService.java) |
| Inventário próprio (anti-overbooking) | ✅ `UPDATE` condicional por noite | [InventoryRepository.java](../src/main/java/br/com/cvc/evaluation/inventory/InventoryRepository.java) |
| Erros RFC 7807 | ✅ todos os mappers | [ProblemDetail.java](../src/main/java/br/com/cvc/evaluation/exceptions/ProblemDetail.java) |
| Correlação por `traceId` | ✅ header + log + corpo de erro | [RequestIdFilter.java](../src/main/java/br/com/cvc/evaluation/web/RequestIdFilter.java) |
| Contrato, health, métricas | ✅ `/q/openapi`, `/q/health`, `/q/metrics` | `quarkus-smallrye-openapi`, `-health`, `micrometer-prometheus` |
| CI | ✅ 3 jobs + CodeQL + Dependabot | [.github/workflows/ci.yml](../.github/workflows/ci.yml) |

**Suíte:** 98 testes em 20 classes; gate de cobertura (`jacoco:check`, 80% por pacote) roda no `mvn verify`.

**Desvio em relação ao plano da v0.2:** a busca continua em `GET /api/v1/booking/{cityCode}/...` em vez de virar `GET /api/v1/hotels?cityCode=...`. A renomeação era cosmética e nunca foi feita; está registrada como dívida em R11.

---

## 2. Restrições e atributos de qualidade

### 2.1 Restrições (não negociáveis)

| # | Restrição | Origem |
|---|---|---|
| C1 | Java 21 + **Quarkus 3.40.1.1**, build Maven | `pom.xml` |
| C2 | O **broker externo não é controlado** por nós: contrato e disponibilidade desconhecidos, sem SLA. Desde 2026-11-03 existe um **projeto nosso** para construir esse broker — [testecvc-broker-quarkus](https://github.com/zekiefa/testecvc-broker-quarkus) | `application.yml` → `http://localhost:3000` |
| C3 | O broker **não expõe estoque/disponibilidade** → inventário tem de ser nosso | ADR-002 |
| C4 | Equipe de 1 pessoa; projeto tem finalidade de estudo | contexto do projeto |
| C5 | PostgreSQL **também em desenvolvimento**, via Dev Services (sem H2/em memória) | ADR-001 |
| C6 | O schema é do **Flyway**; o Hibernate nunca o altera (`schema-management.strategy=none`) | ADR-001 |

### 2.2 Atributos de qualidade

| Atributo | Alvo | Como está garantido |
|---|---|---|
| **Consistência (dinheiro e estoque)** | **Forte, transacional** ⬅ restrição mais forte | `UPDATE` condicional por noite na mesma transação da reserva; `CHECK` no banco; snapshot de preço |
| Idempotência | 100% dos `POST /bookings` | `UNIQUE(idempotency_key)` + replay 200; corrida resolvida em transação nova (M1.6) |
| Auditoria | Toda transição registrada | `booking_event`, mesmo commit |
| Disponibilidade (SLO) | 99% leitura; escrita tolerante a retry | Broker fora → `503` explícito, nunca dado inventado |
| Latência | p99 < 300 ms leitura, < 600 ms escrita | Medível em `/q/metrics` (`http_server_requests`) |
| Custo | 1 processo + 1 Postgres | Sem cluster, cache distribuído ou mensageria |
| Segurança | Sem segredo no repositório; 401 genérico; 404 para recurso alheio | chaves por env; `BCryptUtil`; `getOwned` centralizado |
| Correlação | Um id por requisição em log e resposta | `RequestIdFilter` + MDC + `traceId` no problem+json |

> **Onde o sistema morre primeiro:** em **dado errado** (overbooking, total inconsistente), não em carga. Foi o que ordenou os marcos.

---

## 3. Arquitetura geral

### 3.1 Containers

```
                    ┌──────────────────────────────────────────────────────────┐
   Cliente HTTP     │  App Quarkus (1 processo, porta 8080)                    │
   ou Agente IA ───▶│                                                          │
   (JWT Bearer)     │  ┌──────────┐  ┌─────────┐  ┌───────────┐  ┌─────────┐  │
                    │  │ identity │  │ catalog │  │ inventory │  │ booking │  │
                    │  └────┬─────┘  └────┬────┘  └─────┬─────┘  └────┬────┘  │
                    │       │             │             │             │       │
                    │       │        ┌────▼─────┐       │      ┌──────▼─────┐ │
                    │       │        │ pricing  │◀──────┼──────│  (casos de │ │
                    │       │        │  (puro)  │       │      │   uso)     │ │
                    │       │        └──────────┘       │      └──────┬─────┘ │
                    │  ┌────▼──────────────┐  ┌────────▼─────────────▼─────┐ │
                    │  │ PostgreSQL (V1–V4)│  │ web: RequestIdFilter (MDC) │ │
                    │  └───────────────────┘  └────────────────────────────┘ │
                    │  ┌───────────────────────────────────────────────────┐ │
                    │  │ infra: /q/openapi · /q/health · /q/metrics        │ │
                    │  └───────────────────────────────────────────────────┘ │
                    └───────────────────────────┬──────────────────────────────┘
                                                ▼
                                 Broker externo (catálogo + tarifas, leitura)
```

Princípio: **um processo, um banco, fronteiras por módulo.** O broker é alcançado por uma porta (`BrokerCatalog`) que traduz falha de transporte em resposta de domínio (`503`/`404`) — nenhum outro módulo conhece `WebApplicationException`.

### 3.2 Módulos (estado real)

| Módulo | Pacote | Responsabilidade | Observação |
|---|---|---|---|
| `identity` | `...identity` | `AppUser`, `UserRepository`, `UserSeeder` | Papéis em `user_profile` |
| `catalog` | `...catalog` | ACL do broker (`BrokerCatalog`) | Porta única para o catálogo |
| `pricing` | `...pricing` | `PricingCalculator`, `RoomQuote` | **Puro**: sem CDI, sem I/O, sem banco |
| `inventory` | `...inventory` | `InventoryService`, `InventoryRepository`, `AvailabilityResource` | SQL nativo: upsert e update condicional |
| `booking` | `...booking` | `Booking`, `BookingEvent`, `BookingRepository`, `BookingCreationService`, `BookingQueryService`, `BookingLifecycleService`, `BookingResource` | Casos de uso + máquina de estados |
| `web` | `...web` | `RequestIdFilter` (MDC, header, `traceId`) | Transversal |
| `exceptions` | `...exceptions` | `CustomException` + mappers RFC 7807 | `ProblemDetail`, `ProblemSlugs` |
| **legado** | `...endpoint`, `...service`, `...domain`, `...broker` | Busca/detalhe de hotel e preço de exibição; `AuthenticationEndpoint`; `TokenService`; `UserService` | Convive com os módulos novos — sobreposição registrada em R12 |

### 3.3 Modelo de dados

Quatro migrações aplicadas (`src/main/resources/db/migration`):

| Tabela | Colunas principais | Constraints |
|---|---|---|
| `booking` (V1, V2) | `id uuid PK`, `idempotency_key`, `user_subject`, `hotel_id`, `room_id`, `check_in`, `check_out`, `adults`, `children`, `status`, `currency char(3)`, `total_amount numeric(12,2)`, `nightly_breakdown jsonb`, `version`, timestamps | `UNIQUE(idempotency_key)`; `CHECK (check_out > check_in)`; guardas de pax e de total; índices por `(hotel_id, room_id, check_in)`, `(status, check_out)`, `(user_subject)` |
| `booking_event` (V1) | `id bigserial`, `booking_id FK`, `type`, `payload jsonb`, `occurred_at` | `ON DELETE CASCADE`; `(booking_id, occurred_at)`; append-only |
| `room_inventory` (V3) | `hotel_id`, `room_id`, `stay_date`, `capacity`, `reserved`, `version` | `PK(hotel_id, room_id, stay_date)`; `CHECK (0 <= reserved <= capacity)` |
| `app_user` / `user_profile` (V4) | `id`, `username UNIQUE`, `password_hash`, `enabled`, `created_at`; `(user_id, role)` | senha **só** bcrypt; papel na tabela de junção |

Decisões embutidas: dinheiro em `numeric` + `BigDecimal` (nunca `double`); preço **congelado** no snapshot; `booking_event` é auditoria no mesmo commit (não é Outbox — ADR-003); inventário materializado sob demanda com capacidade padrão **1** por quarto/noite (premissa nossa, ADR-002).

### 3.4 Máquina de estados

```
                    ┌─────────────┐
   POST /bookings ─▶│  CONFIRMED  │◀── idempotente por Idempotency-Key
                    └──┬───┬───┬──┘
       cancel           │   │   │        (data de check-in passou, sem check-in)
          ┌─────────────┘   │   └──────────────────┐
          ▼                 │                      ▼
   ┌────────────┐           │              ┌────────────┐
   │ CANCELLED  │           │              │  NO_SHOW   │
   └────────────┘           │              └────────────┘
                     check-in (data >= check_in)
                            ▼
                   ┌──────────────┐
                   │  CHECKED_IN  │
                   └──────┬───────┘
                check-out │
                          ▼
                  ┌───────────────┐
                  │  CHECKED_OUT  │
                  └───────────────┘
```

O grafo é dados (`BookingStatus.allowedTransitions`), não `if`s espalhados. Transição fora dele → **409**. Estados finais não reabrem. `PENDING` fica reservado para quando houver pagamento.

### 3.5 Superfície de API (implementada)

| Método | Caminho | Papel | Estado |
|---|---|---|---|
| `POST` | `/auth` | público | ✅ 401 genérico |
| `GET` | `/api/v1/booking/{cityCode}/{checkIn}/{checkOut}/{adults}/{children}` | `user` | ✅ legado (dívida R11) |
| `GET` | `/api/v1/hotels/{id}` | `user` | ✅ |
| `GET` | `/api/v1/hotels/{id}/availability?checkIn&checkOut` | `user` | ✅ por noite + mínimo |
| `POST` | `/api/v1/bookings` + `Idempotency-Key` (`ratePlanId` obrigatório) | `user` | ✅ 201 / 200 no replay |
| `GET` | `/api/v1/bookings/{id}` | `user` (dono) | ✅ 404 para terceiros |
| `POST` | `/api/v1/bookings/{id}/cancel` | `user` (dono) | ✅ 409 se o estado não permitir |
| `POST` | `/api/v1/bookings/{id}/check-in` | `user` (dono) | ✅ regra de data |
| `POST` | `/api/v1/bookings/{id}/check-out` | `user` (dono) | ✅ exige `CHECKED_IN` |
| `POST` | `/api/v1/bookings/{id}/no-show` | `user` (dono) | ✅ após a data, sem check-in |
| `GET` | `/q/openapi`, `/q/health`, `/q/metrics` | ops | ✅ |

Erros sempre `application/problem+json` com `type` estável por classe (`…/booking-period-invalid`, `…/invalid-booking-state`, `…/broker-unavailable`, `…/validation-failed`) e `traceId` como extensão. Datas em ISO-8601; entrada inválida → `400`, nunca `500`.

### 3.6 Fluxo crítico — criar reserva (implementado)

1. Autenticar e autorizar (`user`) — 401 se ausente.
2. Ler `Idempotency-Key`; se já existe **e o dono confere**, devolver a reserva (`200`) sem efeitos. Chave de outro dono → **409** (não vaza dados de terceiros).
3. Validar: `checkOut > checkIn`, ≤ 30 noites, `adults ≥ 1`, `children ≥ 0`.
4. Ler a cotação do período no broker v1 (`POST /v1/quotes`, tarifa por data de estadia); falha → `503`; hotel/quarto/plano inexistente → `404`; noite sem tarifa publicada → `409 rate-unavailable`. Cotação que não cobre o período ou em outra moeda é tratada como indisponibilidade.
5. Aplicar a taxa **por noite** sobre a tarifa líquida do broker e somar (`PricingCalculator`, 500 bps, `HALF_UP`, escala 2); o detalhamento guarda uma linha por data.
6. Em **uma transação**: materializar o inventário do período e, por noite em ordem de data, `UPDATE … SET reserved = reserved + 1 WHERE … AND reserved + 1 <= capacity`; noite sem linha afetada → rollback + `409`.
7. Inserir `booking` (`CONFIRMED`, `version = 0`) e `booking_event(CREATED)`; commit.
8. `201` + `Location` (ou `200` no replay). Corrida entre requisições simultâneas: quem perde relê a vencedora em transação nova.

### 3.7 Decisões de implementação que não viraram ADR

| Decisão | Onde | Por quê |
|---|---|---|
| `mapping.format.global=ignore` | `application.yml` | Hibernate recusa usar o Jackson compartilhado com o REST em colunas jsonb |
| Relatório do JaCoCo na fase `test` | `pom.xml` | o job de testes do CI não precisa empacotar para ter cobertura |
| `RequestIdFilter` limpa o MDC na resposta | `web/` | thread é reutilizada; id vazado contamina a requisição seguinte |
| Seeder desligado por padrão | `application.yml` | semear senha padrão em produção seria porta aberta |
| 404 (não 403) para reserva de terceiro | `BookingQueryService` | 403 confirmaria a existência do id |
| SQL nativo no inventário | `InventoryRepository` | upsert e update condicional são operações de conjunto; o banco decide |

---

## 4. Decisões (ADRs)

| # | Decisão | Status | Alternativa principal descartada |
|---|---|---|---|
| [ADR-001](adr/ADR-001-monolito-modular.md) | Monólito modular + PostgreSQL + Flyway (Postgres também em dev) | Aceita | Microsserviços + Kafka; permanecer stateless |
| [ADR-002](adr/ADR-002-fonte-da-verdade.md) | Broker = catálogo/tarifas; reservas e inventário são fonte da verdade local | Aceita | Delegar disponibilidade ao broker (impossível: não expõe) |
| [ADR-003](adr/ADR-003-idempotencia-e-maquina-de-estados.md) | `Idempotency-Key` + máquina de estados explícita + `@Version` | Aceita | Lock pessimista; idempotência por hash do payload |
| [ADR-004](adr/ADR-004-semantica-da-taxa.md) | Taxa em basis points + `PricingCalculator` puro + snapshot de preço | Aceita — **valor definido: 5% (500 bps)**, DEC-0001 | Manter `0.70` como markup de 70% |

---

## 5. Marcos

| Marco | Conteúdo | Aceite | Estado |
|---|---|---|---|
| **M0 — Higiene** | `.gitattributes`, rotação de chaves, `PricingCalculator` em bps, validação, 401 | `mvn verify` verde; preço de 3 noites fecha; nenhum segredo versionado | ✅ mergeado |
| **M1 — Reserva persistida** | Postgres/Panache/Flyway, V1, `POST/GET/cancel`, estados, idempotência | mesma chave → 1 reserva; transição inválida → 409; corrida → replay | ✅ PRs #2, #5, #6, #9, #8, #10 |
| **M2 — Disponibilidade e ciclo de vida** | `room_inventory`, `/availability`, hold transacional, check-in/out/no-show | 6 clientes na mesma noite → 1×201, 5×409, `reserved == capacity` | ✅ PRs #11, #12 |
| **M3 — Contrato e operação** | OpenAPI, health, métricas, RFC 7807, identidade em banco, `traceId`, CI | `/q/openapi` e `/q/health` respondem; CI verde; 98 testes | ✅ PRs #14–#18 |
| **M4 — IA (opcional)** | Agente consumindo os casos de uso via OpenAPI | agente propõe, serviço determinístico executa | ⏸️ pausado (tokens) |

### 5.1 Não faremos até haver sinal

| Item | Sinal que autoriza |
|---|---|
| Microsserviços | deploy/escala independente **e** time para operar |
| Kafka / Outbox | existir consumidor externo de eventos |
| CQRS / Event Sourcing | consulta analítica pesada ou exigência legal de auditoria |
| Cache de catálogo | p99 do broker degradar a leitura além do alvo |
| Kubernetes | mais de um ambiente com orquestração real |
| Multi-tenant / pagamento | requisito de produto, com ADR próprio |
| LLM no caminho da reserva | **nunca** — o modelo propõe, o serviço determinístico executa |

---

## 6. Revisão e riscos

| # | Risco | Sev. | Situação / mitigação |
|---|---|---|---|
| R1 | Chaves privadas no histórico do Git | Alta | **Mitigado**: par rotacionado, chaves fora do repositório, carga por env. O histórico continua público — o par antigo deve ser considerado comprometido |
| R2 | Valor comercial da taxa | Média | **Decidido** (DEC-0001: 5%). Validação por cotação tentada e inconclusiva (VAL-0001: spread de 167% entre sites, produtos não equivalentes); critério trocado para "broker real comparável ou dados de conversão" |
| R3 | Broker sem contrato/SLA | Média | Porta + `503` explícito; sem fallback inventado |
| R4 | `capacity` padrão = 1 é premissa nossa | Média | Parametrizável; validar quando houver contrato de estoque |
| R5 | `room_inventory` cresce com hotéis × quartos × dias | Baixa | Aceitável nesta escala; agregar por período se doer |
| R6 | *Boundary drift* entre módulos | Média | Regra de dependência na §3.2; ArchUnit ainda não implementado |
| R7 | Check-in/out simulados, sem PMS real | Baixa | Declarado como fora de escopo |
| R8 | **Sem Docker, `mvn verify` falha com mensagem enganosa** | Média | Sem o daemon, os 18 `@QuarkusTest` não sobem (Dev Services) e o surefire reporta `Could not load class` no *discovery* — sintoma que parece defeito da ferramenta. Diagnosticar com `docker info`, não com a mensagem; CI cobre a suíte |
| R9 | Atualização da BOM do Quarkus | Média | Extensão isolada quebra (caso `resteasy-problem`); tratar o PR do Dependabot manualmente |
| R10 | Identidade só tem seeder (sem provisionamento) | Média | Só vira problema com multi-usuário; exige endpoint/admin + ADR |
| R11 | Busca em `/api/v1/booking/{...}` (nome enganoso) | Baixa | Dívida cosmética; renomear quando houver consumidor externo |
| R12 | Legado (`endpoint`/`service`/`domain`) sobreposto aos módulos novos | Média | `BookingService` ainda faz busca + preço de exibição; migrar para `catalog` na próxima passagem |
| R13 | Build nativo (`-Dnative`) não exercitado | Baixa | Perfil existe no `pom`; testar quando houver alvo de entrega |

**Já coberto:** falha do broker, idempotência (inclusive concorrente), autorização por dono, consistência de estoque e de total, auditoria de transições, preço congelado, correlação por `traceId`, contrato publicado, CI com Sonar/CodeQL/Dependabot.

**Não coberto (consciente):** pagamento, reembolso, notificação, rate limiting, multi-tenant, LGPD/anonimização, DR/backup, tracing distribuído.

---

## 7. Referências

- **tutorial:11 — Engenharia de consistência de dados.** Idempotência como base da correção; compensação não é rollback; Outbox só quando há entrega. Base do ADR-003.
- **tutorial:19 — Walkthrough de design de complexidade média.** "O modelo propõe, o serviço determinístico executa"; separação por risco. Base do M4.
- **tutorial:34 — Árvore de decisão de escolha tecnológica.** Primeiro "preciso de tecnologia nova?"; modo de falha antes do instrumento; ADR com alternativa, gatilho e plano de saída. Base do ADR-001/002.
- **tutorial:07 — Do zero a um sistema.** Sequência requisitos → escala → casos de uso → dados → containers → decisões. Base da estrutura deste documento.

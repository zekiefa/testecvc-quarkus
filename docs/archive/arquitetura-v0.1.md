# Arquitetura — API de Reservas de Hotel (testecvc-quarkus)

| Campo | Valor |
|---|---|
| Versão | **v0.1** |
| Data | 2026-10-28 |
| Status | Direções aprovadas (D1–D3); execução não iniciada |
| Documentos relacionados | [ADR-001](adr/ADR-001-monolito-modular.md) · [ADR-002](adr/ADR-002-fonte-da-verdade.md) · [ADR-003](adr/ADR-003-idempotencia-e-maquina-de-estados.md) · [ADR-004](adr/ADR-004-semantica-da-taxa.md) |
| Base analisada | commit `aca4b49` (Quarkus 3.12.3, Java 21) |

## Histórico de versões

| Versão | Data | Mudança |
|---|---|---|
| v0.1 | 2026-10-28 | Versão inicial: levantamento do *as-built*, restrições, arquitetura-alvo em 5 marcos e 4 ADRs. |

---

## 1. Posicionamento do sistema

> **Uma frase:** API REST em Quarkus que vende e gerencia reservas de quartos de hotel — consulta catálogo e tarifas de um broker externo, mantém **o próprio inventário** e o ciclo de vida da reserva (criação, consulta, check-in, check-out, cancelamento).

| Dimensão | Definição |
|---|---|
| Quem usa | Cliente HTTP autenticado por JWT (role `user`). Futuramente também um **agente de IA** como cliente de primeira classe, via contrato OpenAPI. |
| Como é acionado | Requisições HTTP síncronas (REST/JSON). Sem filas, sem jobs assíncronos nesta fase. |
| Natureza dos dados | **Volátil e externo:** catálogo e tarifas do broker (leitura). **Durável e nosso:** reservas, inventário, usuários, eventos de reserva (PostgreSQL). |
| Fronteira do sistema | Busca e detalhe de hotel, disponibilidade, criação/consulta/cancelamento de reserva, check-in/check-out, autenticação e autorização. |
| **Fora de escopo (hoje)** | Pagamento, reembolso, faturamento, notificações/e-mail, multi-tenant, integração com PMS real, cancelamento parcial de diárias, IA/LLM. |

### 1.1 De onde partimos (as-built)

O código atual **não é um sistema de reservas**: é um *price quote + catálogo* stateless. Não existe entidade, tabela, endpoint de escrita nem inventário. O nome "booking" descreve a intenção, não o comportamento.

| Capacidade | As-built | Evidência |
|---|---|---|
| Buscar hotéis por cidade | ✅ consulta sobre o broker | [BookingService.java:103](../src/main/java/br/com/cvc/evaluation/service/BookingService.java#L103) |
| Detalhe de hotel com preço | ✅ | [BookingService.java:91](../src/main/java/br/com/cvc/evaluation/service/BookingService.java#L91) |
| Criar/consultar/cancelar reserva | ❌ inexistente | não há `POST` nem persistência |
| Disponibilidade | ❌ inexistente (assume-se estoque infinito) | [BrokerService.java:16](../src/main/java/br/com/cvc/evaluation/broker/BrokerService.java#L16) |
| Check-in / check-out | ❌ inexistente | — |
| Autenticação | ✅ JWT RS256 + `@RolesAllowed("user")` | [AuthenticationEndpoint.java:18](../src/main/java/br/com/cvc/evaluation/endpoint/AuthenticationEndpoint.java#L18) |
| Erros RFC 7807 | ⚠️ parcial: mapper devolve texto puro | [EvaluationExceptionHandler.java:11](../src/main/java/br/com/cvc/evaluation/exceptions/EvaluationExceptionHandler.java#L11) |

Defeitos conhecidos que esta evolução corrige: taxa excluída do total, perna de 0 noites aceita, data malformada virando 500, 404/403 no login (enumeração de usuário), chaves privadas versionadas, ausência de validação de entrada e de normalização de EOL.

---

## 2. Restrições e atributos de qualidade

### 2.1 Restrições (não negociáveis)

| # | Restrição | Origem |
|---|---|---|
| C1 | Java 21 + Quarkus 3.12.x, build Maven | `pom.xml` |
| C2 | O **broker externo não é controlado** por nós: contrato e disponibilidade desconhecidos, sem SLA | `application.yml` → `http://localhost:3000` |
| C3 | O broker **não expõe estoque/disponibilidade** (confirmado) → inventário tem de ser nosso | decisão D1 |
| C4 | Equipe de 1 pessoa; projeto tem finalidade de estudo | contexto do projeto |
| C5 | PostgreSQL **também em desenvolvimento** (sem H2/em memória) | decisão do time |

### 2.2 Atributos de qualidade alvo

| Atributo | Alvo | Observação |
|---|---|---|
| **Consistência (dinheiro e estoque)** | **Forte, transacional** | ⬅ **restrição mais forte**. Nunca vender o mesmo quarto duas vezes na mesma noite; nunca gravar total que não fecha com as diárias. |
| Idempotência | 100% dos `POST /bookings` | Retry do cliente não duplica reserva. |
| Auditoria | Toda transição de estado registrada | `booking_event`, na mesma transação. |
| Disponibilidade (SLO) | 99% mensal para leitura; 99,5% para escrita tolerante a retry | Sem SLA do broker; indisponibilidade do broker = degradação declarada (503), não dado inventado. |
| Latência | p99 < 300 ms leitura, < 600 ms escrita (1 região) | Sem requisito de throughput relevante nesta fase. |
| Custo | 1 processo + 1 Postgres | Nada de cluster, cache distribuído ou mensageria. |
| Segurança | Sem segredo no repositório; 401 genérico no login; validação de entrada em toda borda | Ver R1. |

> **Onde o sistema morre primeiro:** em **dado errado** (overbooking, total inconsistente), não em carga. Isso é o que ordena as prioridades dos marcos.

---

## 3. Arquitetura geral

### 3.1 Visão de containers (alvo)

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
                    │  ┌────▼──────────────────┐  ┌──────▼─────────────▼────┐ │
                    │  │ UserRepository (porta)│  │  PostgreSQL (Flyway)   │ │
                    │  │  → config hoje / BD   │  │  booking · inventory   │ │
                    │  └───────────────────────┘  │  booking_event · user  │ │
                    │  ┌───────────────────────┐  └────────────────────────┘ │
                    │  │ BrokerClient (porta)  │                                 │
                    │  │  ACL → REST Client    │──┐                              │
                    └──┴───────────────────────┴──┼──────────────────────────────┘
                                                  ▼
                                   Broker externo (catálogo + tarifas, leitura)
```

Princípio: **um processo, um banco, fronteiras por módulo.** O broker é sempre alcançado por uma porta (`BrokerClient`) e traduzido por uma camada anticorrupção — o domínio (`Hotel`, `Room`) nunca conhece os DTOs `BrokerHotel`/`Price`.

### 3.2 Módulos

| Módulo | Pacote | Responsabilidade | Regra de dependência |
|---|---|---|---|
| `identity` | `...identity` | Login, emissão de JWT, usuários e papéis | Não depende de nenhum outro módulo |
| `catalog` | `...catalog` | Busca/detalhe de hotéis, disponibilidade exposta, ACL do broker | Depende de `inventory` (leitura) e `pricing`; **não** de `booking` |
| `pricing` | `...pricing` | Cálculo de tarifa e total, arredondamento, snapshot de preço | **Puro**: sem CDI, sem I/O, sem banco |
| `inventory` | `...inventory` | Reserva de estoque por noite, capacidade, sincronização | Não depende de `booking` (é dependido) |
| `booking` | `...booking` | Casos de uso da reserva, máquina de estados, idempotência, eventos | Depende de `catalog` (porta), `inventory`, `pricing` |
| `shared` | `...shared` | Problem Details (RFC 7807), exceções de domínio, config | Kernel mínimo — nada de regra de negócio |

Enforcement: revisão de imports no PR + teste ArchUnit barato (opcional, M3) proibindo `booking ← catalog` e `pricing ← *`.

### 3.3 Modelo de dados

| Tabela | Colunas principais | Índices/constraints |
|---|---|---|
| `booking` | `id uuid PK`, `idempotency_key text`, `hotel_id int`, `room_id int`, `check_in date`, `check_out date`, `adults int`, `children int`, `status`, `currency char(3)`, `total_amount numeric(12,2)`, `nightly_breakdown jsonb`, `version bigint`, `created_at`, `updated_at` | `UNIQUE(idempotency_key)`; `(hotel_id, room_id, check_in)`; `(status, check_out)`; `CHECK (check_out > check_in)` |
| `booking_event` | `id bigserial PK`, `booking_id FK`, `type`, `payload jsonb`, `occurred_at` | `(booking_id, occurred_at)`; append-only |
| `room_inventory` | `hotel_id int`, `room_id int`, `stay_date date`, `capacity int`, `reserved int`, `version bigint` | `PK(hotel_id, room_id, stay_date)`; `CHECK (reserved >= 0 AND reserved <= capacity)` |
| `app_user` / `user_profile` | `id`, `username UNIQUE`, `password_hash`, `enabled`; `profile(role)` | Populado em M3; em M1 permanece a config, atrás da porta `UserRepository` |

Decisões embutidas: valor monetário sempre `numeric` + `BigDecimal` (nunca `double`); preço **congelado na reserva** (`nightly_breakdown`) para que mudança de tarifa no broker não altere reserva já confirmada; `booking_event` é log de auditoria **no mesmo commit** (não é Outbox — ver ADR-003).

Materialização do inventário: **sob demanda** (upsert por noite na primeira reserva do quarto), com `capacity` padrão **1** por quarto/noite. Sincronização completa com o broker é tarefa posterior, disparada por sinal (ver M3).

### 3.4 Máquina de estados da reserva

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

Transições fora do grafo → **409 Conflict**. O estado `PENDING` fica reservado para quando houver pagamento (entra junto com a saga de compensação, não antes).

### 3.5 Superfície de API (alvo)

| Método | Caminho | Papel | Mudança |
|---|---|---|---|
| `POST` | `/auth` | público | corrigir 404/403 → **401** genérico |
| `GET` | `/api/v1/hotels?cityCode&checkIn&checkOut&adults&children` | `user` | substitui `/api/v1/booking/{...}` (sem consumidores externos) |
| `GET` | `/api/v1/hotels/{id}` | `user` | mantém |
| `GET` | `/api/v1/hotels/{id}/availability?checkIn&checkOut` | `user` | **novo** |
| `POST` | `/api/v1/bookings` + header `Idempotency-Key` | `user` | **novo** |
| `GET` | `/api/v1/bookings/{id}` | `user` (dono) | **novo** |
| `POST` | `/api/v1/bookings/{id}/check-in` | `user` (dono) | **novo** |
| `POST` | `/api/v1/bookings/{id}/check-out` | `user` (dono) | **novo** |
| `POST` | `/api/v1/bookings/{id}/cancel` | `user` (dono) | **novo** |
| `GET` | `/q/openapi`, `/q/health` | ops | **novo** (M3) |

Erros sempre `application/problem+json` (`type`, `title`, `status`, `detail`, `instance`), incluindo validação (`400`) e conflito de estado (`409`). Datas em ISO-8601 (`yyyy-MM-dd`); entrada inválida → `400`, nunca `500`.

### 3.6 Fluxo crítico — criar reserva

1. Autenticar e autorizar (`user`) — 401 se ausente.
2. Ler `Idempotency-Key`; se já existe, **devolver a reserva existente** (mesmo corpo, `200`) sem novos efeitos.
3. Validar entrada: `checkOut > checkIn`, estadia ≤ 30 noites, `adults ≥ 1`, `children ≥ 0`.
4. Carregar tarifa atual via porta do broker; broker indisponível → `503` (nunca inventar preço).
5. Calcular `nightly_breakdown` + `total_amount` com `pricing` (arredondamento `HALF_UP`, escala 2).
6. Em **uma transação**: para cada noite em ordem crescente de data, `UPDATE room_inventory SET reserved = reserved + 1 WHERE ... AND reserved + 1 <= capacity`; qualquer noite sem linha afetada → rollback + `409`.
7. Inserir `booking` (status `CONFIRMED`, `version = 0`) e `booking_event(CREATED)`; commit.
8. Retornar `201` + `Location` (ou `200` no replay idempotente).

### 3.7 O que muda em relação ao as-built

| Hoje | Alvo |
|---|---|
| Stateless | Estado transacional em PostgreSQL |
| `BookingService` faz busca + preço + formatação | `catalog` (leitura) e `booking` (escrita) separados; `pricing` puro |
| Preço calculado no service, sem teste de valor | `PricingCalculator` com testes de 1 e 3 noites e arredondamento |
| Broker acoplado ao domínio | Porta `BrokerClient` + ACL |
| Erro = string | Problem Details consistente |
| Sem contrato | OpenAPI gerado (`/q/openapi`) |

---

## 4. Decisões (ADRs)

| # | Decisão | Status | Alternativa principal descartada |
|---|---|---|---|
| [ADR-001](adr/ADR-001-monolito-modular.md) | Monólito modular + PostgreSQL + Flyway (Postgres também em dev) | Aceita | Microsserviços + Kafka; permanecer stateless |
| [ADR-002](adr/ADR-002-fonte-da-verdade.md) | Broker = catálogo/tarifas; reservas e inventário são fonte da verdade local | Aceita | Delegar disponibilidade ao broker (impossível: não expõe) |
| [ADR-003](adr/ADR-003-idempotencia-e-maquina-de-estados.md) | `Idempotency-Key` + máquina de estados explícita + `@Version` | Aceita | Lock pessimista; idempotência por hash do payload |
| [ADR-004](adr/ADR-004-semantica-da-taxa.md) | Taxa em basis points + `PricingCalculator` puro + snapshot de preço | Aceita — valor comercial **em aberto** (7% é provisório, de teste) | Manter `0.70` como markup de 70% |

---

## 5. Marcos

Cada marco é um PR revisável e reversível. Nenhum marco depende de infraestrutura que a equipe não saiba operar.

| Marco | Conteúdo | Aceite (critério objetivo) | Sinal que autoriza o próximo |
|---|---|---|---|
| **M0 — Higiene** | `.gitattributes` + normalização de EOL; **rotacionar chaves JWT** (gerar novo par RS256, revogar o antigo, carga por env/secret); `PricingCalculator` + ADR-004 aplicado; validação de entrada; login 401 | `mvn verify` verde; teste de preço de 3 noites fecha com as diárias; `git status` limpo; nenhum segredo versionado e **par antigo revogado** | Build reproduzível e preço confiável |
| **M1 — Reserva persistida** | Postgres + Panache + Flyway; tabelas `booking`/`booking_event`; `POST/GET/cancel`; idempotência; `@Version` | Duas requisições com a mesma `Idempotency-Key` → **1** reserva; `checkOut <= checkIn` → 400; transição inválida → 409; integração contra Postgres real (Dev Services) | Taxa de erro de escrita < 1% por 2 semanas; nenhuma reserva duplicada |
| **M2 — Disponibilidade + check-in/out** | `room_inventory`; `GET /availability`; hold transacional; `check-in`/`check-out` | Teste concorrente (N threads, mesmo quarto/noite) **não** produz overbooking; check-in antes da data → 409 | Primeiro uso real de check-in/out, ou pedido de cancelamento com reembolso |
| **M3 — Contrato e operação** | OpenAPI, health, métricas, CI, usuários em BD, logs com `traceId` | `/q/openapi` e `/q/health` respondem; PR sem CI verde não passa; latência p99 medida | p99 > 300 ms, ou broker instável, ou necessidade de cache |
| **M4 — IA (opcional)** | Agente consumindo os casos de uso como ferramentas; RAG de políticas em serviço separado | Agente **propõe** reserva, serviço determinístico executa; nenhuma ação irreversível sem confirmação | Existir valor demonstrado em M1–M3 |

### 5.1 Não faremos até haver sinal

| Item | Sinal que autoriza |
|---|---|
| Microsserviços | Necessidade real de deploy/escala independente **e** time para operar |
| Kafka / Outbox | Existir consumidor externo de eventos (ex.: e-mail, faturamento) |
| CQRS / Event Sourcing | Consulta analítica pesada ou auditoria legal — não "por elegância" |
| Cache de catálogo | p99 do broker degradar a leitura além do alvo |
| Kubernetes | Mais de um ambiente com necessidade de orquestração real |
| Multi-tenant / pagamento | Novo requisito de produto, com ADR próprio |
| LLM no caminho da reserva | **Nunca** — o modelo propõe, o serviço determinístico executa |

---

## 6. Revisão e riscos remanescentes

| # | Risco | Sev. | Mitigação |
|---|---|---|---|
| R1 | Chaves privadas RSA no histórico do Git | **Alta** | Gerar novo par RS256 e **revogar o antigo** (o histórico já vazou); remover do tracking e carregar de env/secret; M0 |
| R2 | Valor comercial da taxa não definido (7% é provisório, de teste local) | Média | `fee-bps` parametrizado por ambiente; teste cobre a fórmula, não o número; definir antes de produção (não bloqueia M0/M1) |
| R3 | Broker sem contrato/SLA; indisponibilidade derruba a busca | Média | Porta + timeout + `503` explícito; sem fallback inventado |
| R4 | `capacity` padrão = 1 por quarto/noite é premissa | Média | Parametrizar; validar com o broker em M2 |
| R5 | `room_inventory` cresce com hotéis × quartos × 365 | Baixa | Aceitável em estudo; agregar por período se doer |
| R6 | *Boundary drift* do monólito modular | Média | Regra de dependência na seção 3.2 + ArchUnit em M3 |
| R7 | Check-in/out são simulados, não integrados a PMS | Baixa | Declarado como fora de escopo |
| R8 | Ausência de CI/observabilidade até M3 | Média | CI entra em M3; até lá, `mvn verify` manual obrigatório |

**Já coberto por este desenho:** falha do broker (timeout + 503), idempotência de escrita, autorização por papel e por dono, consistência de estoque e de total, auditoria de transições, preço congelado por reserva.

**Não coberto (consciente):** pagamento, reembolso, notificação, rate limiting, multi-tenant, LGPD/anonimização, DR/backup.

---

## 7. Referências

- **tutorial:11 — Engenharia de consistência de dados.** Idempotência como base da correção (chave de idempotência + deduplicação; retry e idempotência andam juntos); compensação não é rollback; CQRS é *over-engineering* para CRUD comum. Base do ADR-003 e da seção 5.1.
- **tutorial:19 — Walkthrough completo de design de complexidade média.** Regra de ouro "o modelo propõe, o serviço determinístico executa" e separação por risco (frequência baixa + alto impacto merece caminho próprio). Base do M4.
- **tutorial:34 — Árvore de decisão de escolha tecnológica.** Primeiro perguntar "preciso mesmo de tecnologia nova?"; estágio (MVP/estudo → menos componentes); localizar o modo de falha antes do instrumento; ADR com alternativa, gatilho de revisão e plano de saída. Base do ADR-001/002.
- **tutorial:07 — Do zero a um sistema.** Sequência requisitos → escala → casos de uso → dados → containers → decisões; base da estrutura deste documento.

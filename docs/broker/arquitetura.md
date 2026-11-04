# Arquitetura — Broker de Hotéis (v0.1)

| Campo | Valor |
|---|---|
| Versão | **v0.1** |
| Data | 2026-11-03 |
| Status | Proposta aprovada (escopo definido); nenhuma linha de código |
| Projeto | **novo e separado** do serviço de reservas — este documento mora aqui até o repositório do broker existir |
| Consumidor inicial | [testecvc-quarkus](../arquitetura.md) (serviço de reservas), no papel de *buyer* |
| Épico | `tcq-ptp` |

---

## 1. Posicionamento do sistema

> **Uma frase:** API REST **B2B** que publica catálogo, tarifas e disponibilidade de hotéis de uma cidade — com **frescor explícito** — para que um comprador consiga cotar uma estadia sem inventar preço.

| Dimensão | Definição |
|---|---|
| Quem usa | Um **buyer** autenticado por API key. No v1, exatamente um: o serviço de reservas |
| Como é acionado | HTTP REST/JSON síncrono. Sem filas, sem jobs assíncronos no v1 |
| Natureza dos dados | **Durável e nosso:** hotéis, quartos, regimes, tarifas por data, disponibilidade publicada. **Nenhum dado pessoal** |
| Fronteira do sistema | Publicar catálogo, cotar estadia e informar disponibilidade. **Não** vende, não cobra, não reserva (v1) |
| **Fora de escopo (v1)** | Pagamento, PMS real, GDS, multi-moeda, precificação dinâmica, multi-tenant, scraping, reserva/hold |

**Ações essenciais** (sem elas a proposta não se sustenta): **publicar tarifa por data** e **cotar**.
Disponibilidade é obrigatória, mas informativa no v1 — ver R5.

### 1.1 Por que este projeto existe

O serviço de reservas depende de um broker que hoje é uma fixture com uma cidade fictícia. Isso
tem duas consequências medidas:

1. **Preço não é validável** — a tentativa de validação de mercado ([VAL-0001](../../.super-pm/decisions.pt-BR.md)) foi inconclusiva porque não existe comparável no broker;
2. **Nada prova o caminho de integração** — contrato, frescor, falha de fornecedor e mapeamento de conteúdo nunca foram exercitados com dado realista.

O broker existe para fechar esse buraco sem exigir um fornecedor comercial.

---

## 2. Restrições e atributos de qualidade

### 2.1 Restrições

| # | Restrição | Origem |
|---|---|---|
| C1 | **1 pessoa**, projeto de estudo, custo ~zero | contexto |
| C2 | Reaproveitar a stack já dominada: Quarkus + PostgreSQL + Flyway + CI | decisão de reaproveitamento |
| C3 | **Sem PMS real** ⇒ a ingestão é por **push/admin + seed**; *scraping* está proibido | ausência de fornecedor |
| C4 | Um único *buyer* no v1 — mas **API key desde o primeiro dia** | evitar retrofit de identidade |
| C5 | Catálogo de **1 cidade × ~10 hotéis** no v1 | escopo aprovado |

### 2.2 Atributos de qualidade

| Atributo | Alvo | Como é garantido |
|---|---|---|
| **Frescor honesto** ⬅ restrição mais forte | toda tarifa devolve `updatedAt` e `staleAfter` | a resposta diz **quando** o dado foi atualizado; o comprador decide se confia |
| Consistência | tarifa e disponibilidade da mesma origem, no mesmo commit | transação única na ingestão |
| Contrato | estável, versionado `/v1`, apenas adição compatível | OpenAPI-first; contrato publicado antes do código |
| Latência | p99 < 100 ms para catálogo/quote (1 região) | read model pré-calculado, sem fan-out no v1 |
| Custo | 1 processo + 1 Postgres | nenhum serviço auxiliar |
| Segurança | API key obrigatória; sem dado pessoal; sem segredo no repositório | filtro de auth + chave por env |
| Observabilidade | `traceId`, health, métricas, erros RFC 7807 | mesmo padrão do serviço de reservas |

> **Onde este sistema morre primeiro:** vendendo **tarifa velha como se fosse atual**. Não é carga — é confiança. Por isso `staleAfter` é atributo de qualidade, não detalhe.

---

## 3. Arquitetura geral

### 3.1 Containers

```
   Operador (você)            Buyer (serviço de reservas)
        │                              │
        │ PUT /admin/...               │ GET /v1/... · POST /v1/quotes
        ▼                              ▼
   ┌──────────────────────────────────────────────────────────┐
   │  broker (1 processo Quarkus, porta 8081)                 │
   │                                                          │
   │  ┌────────────┐   ┌──────────────────┐   ┌────────────┐  │
   │  │ ingestion  │──▶│ catálogo/tarifas │──▶│  API v1    │  │
   │  │ (admin)    │   │  (read model)    │   │ (buyer)    │  │
   │  └────────────┘   └──────────────────┘   └────────────┘  │
   │         │                  │                    │        │
   │         └──────────┬───────┘                    │        │
   │                    ▼                            ▼        │
   │            PostgreSQL (Flyway)        api key · traceId  │
   │                                      problem+json        │
   └──────────────────────────────────────────────────────────┘
```

Um processo, um banco. Sem cache distribuído: o *read model* **é** a tabela.

### 3.2 Módulos

| Módulo | Responsabilidade | Regra |
|---|---|---|
| `catalog` | `Hotel`, `RoomType`, `RatePlan` | conteúdo; não conhece preço |
| `rates` | `Rate` por data/regime/pax, moeda | única fonte de tarifa |
| `availability` | `Availability` por data — allotment e stop-sell publicados | não bloqueia nada no v1 (R5) |
| `ingestion` | endpoints de admin (push) + seed declarado | só escreve; não lê para fora |
| `quoting` | soma de diárias, arredondamento `HALF_UP`, frescor | **puro**, sem I/O |
| `api` | filtros de API key, `traceId`, mapeamento de erro | borda |
| `mapping` | IDs externos (`provider`, `externalId`) | preparado para o agregador futuro |

### 3.3 Modelo de dados

| Tabela | Colunas principais | Constraints |
|---|---|---|
| `hotel` | `id`, `name`, `city_code`, `city_name`, `latitude`, `longitude`, `data_source` (`SEED`/`PUSH`), `updated_at` | `(city_code)` |
| `room_type` | `id`, `hotel_id FK`, `name`, `max_adults`, `max_children`, `capacity` | `FK hotel` |
| `rate_plan` | `id`, `room_type_id FK`, `name`, `board` (`RO`/`BB`/`HB`/`FB`), `cancellation_policy` | `FK room_type` |
| `rate` | `hotel_id`, `room_type_id`, `rate_plan_id`, `stay_date`, `adult_amount`, `child_amount`, `currency char(3)`, `updated_at` | `PK(room_type_id, rate_plan_id, stay_date)`; `CHECK (adult_amount >= 0)` |
| `availability` | `room_type_id`, `stay_date`, `allotment`, `stop_sell` | `PK(room_type_id, stay_date)`; `CHECK (allotment >= 0)` |
| `api_client` | `id`, `name`, `key_hash`, `enabled`, `rate_limit_per_min` | `UNIQUE(key_hash)` |
| `mapping` *(vazio no v1)* | `entity_type`, `entity_id`, `provider`, `external_id` | `UNIQUE(provider, external_id)` |

Decisões embutidas: dinheiro em `numeric` + `BigDecimal`; **`data_source` é parte do contrato** —
dado sintético nunca se disfarça de real (é a armadilha que o *fixture* atual criou); tarifa é por
**data de estadia** (`stay_date`), não por período — o `quote` soma as noites.

### 3.4 Contrato de API (v1)

**Buyer** (API key no header `X-Api-Key`):

| Método | Caminho | Devolve |
|---|---|---|
| `GET` | `/v1/hotels?cityCode=&limit=&offset=` | lista paginada |
| `GET` | `/v1/hotels/{id}` | hotel + quartos + regimes |
| `POST` | `/v1/quotes` | preço por diária e total, com `breakdown` e `staleAfter` |
| `GET` | `/v1/availability?roomTypeId=&checkIn=&checkOut=` | por noite + mínimo do período |

**Admin** (API key com papel `admin`):

| Método | Caminho | Efeito |
|---|---|---|
| `PUT` | `/admin/hotels/{id}` | cria/atualiza catálogo |
| `PUT` | `/admin/rooms/{roomTypeId}/rates` | publica tarifas por intervalo de datas |
| `PUT` | `/admin/rooms/{roomTypeId}/availability` | publica allotment/stop-sell |

**Sempre**: `/q/openapi`, `/q/health`, `/q/metrics`; erros `application/problem+json` com
`type` estável e `traceId`.

`POST /v1/quotes` — corpo e forma da resposta:

```json
{ "hotelId": 1, "roomTypeId": 1, "ratePlanId": 1,
  "checkIn": "2027-04-10", "checkOut": "2027-04-13", "adults": 1, "children": 1 }
```

```json
{ "totalAmount": 1417.50, "currency": "BRL",
  "nights": [ { "date": "2027-04-10", "adult": 900.00, "child": 450.00 } ],
  "updatedAt": "2026-11-03T12:00:00Z", "staleAfter": "2026-11-03T12:15:00Z",
  "dataSource": "SEED" }
```

O comprador vê tarifa **e** validade. Sem `staleAfter`, o campo `staleAfter` seria decorativo —
por isso ele é obrigatório e o serviço de reservas pode (deve) recusar dado vencido.

### 3.5 Fluxo crítico — publicar tarifa e cotar

1. Operador envia `PUT /admin/rooms/{id}/rates` com um intervalo e preços por pax.
2. Validação: datas em ordem, preço ≥ 0, moeda ISO, regime existente.
3. Transação única: upsert de `rate` por `stay_date`; `updated_at = now()`; recalcula o *read model*.
4. Buyer chama `POST /v1/quotes`; o serviço lê as diárias **todas** do período.
5. Faltou alguma noite → **409** `incomplete-rate-period` (nunca cotar período com buraco).
6. Resposta com `breakdown`, `totalAmount`, `updatedAt`, `staleAfter`, `dataSource`.

### 3.6 Caminho aditivo para alocador (v2, só com sinal)

O contrato do v1 já deixa espaço: `availability` publica `allotment`, e o `quote` devolve frescor.
Para virar alocador acrescenta-se — **sem quebrar** o que existe:

```
POST /v1/holds      → segura allotment por N minutos (idempotente por Idempotency-Key)
POST /v1/bookings   → confirma o hold; expira sozinho se não confirmar
DELETE /v1/holds/{id}
```

É aí que o inventário muda de dono — e o [ADR-002](../adr/ADR-002-fonte-da-verdade.md) do serviço
de reservas precisa ser revisado, porque ele hoje afirma que o broker não tem estoque.

---

## 4. Decisões (ADRs candidatos)

Estas decisões nascem aqui e viram ADRs numerados quando o código começar.

| # | Decisão | Adotado | Alternativa descartada (e o custo dela) |
|---|---|---|---|
| B1 | **Feed, não alocador** no v1 | publica catálogo/tarifas/disponibilidade; quem vende é o comprador | **Alocador**: resolve reserva real, mas move o inventário, exige hold/expiração e invalida o ADR-002 — fazer depois, com sinal |
| B2 | **REST/JSON + OpenAPI-first** | simples, estável, debugável | **GraphQL** (modelo da Travelgate): poderoso para agregação, mas exige governança de query e cache que não temos; **gRPC**: ótimo interno, ruim como API externa |
| B3 | **Frescor explícito** (`updatedAt`/`staleAfter`) | o comprador decide com informação | **Cache silencioso**: engana o comprador e é exatamente o defeito que queremos evitar |
| B4 | **API key + rate limit por cliente** | B2B simples, sem OAuth | **OAuth2 client credentials**: correto em escala, cerimônia desnecessária para 1 buyer; **mTLS**: forte, operacionalmente caro |
| B5 | **Ingestão por push/admin + seed declarado** | funciona sem PMS | **Pull de PMS**: impossível sem PMS; **scraping**: proibido (legal e tecnicamente frágil) |
| B6 | **Tarifa por `stay_date`** | soma de noites é trivial e auditável | **Tarifa por faixa/período**: menos linhas, mais regra de interseção e mais bug |

**Pontos de travamento (lock-in):** o formato das respostas de `/v1` e o esquema da API key. Depois
de publicados, mudam só por adição.

---

## 5. Marcos

| Marco | Conteúdo | Aceite |
|---|---|---|
| **MB0 — Contrato e esqueleto** | OpenAPI v1 publicado, filtro de API key, RFC 7807, `traceId`, health/métricas, CI | `/q/openapi` lista os 4 endpoints; sem chave → 401 problem+json |
| **MB1 — Catálogo e cotação** | `hotel`/`room_type`/`rate_plan`/`rate`, seed de Serra Negra (~10 hotéis), `POST /v1/quotes` | cotação de 3 noites fecha com a soma das diárias; noite faltando → 409 |
| **MB2 — Ingestão e disponibilidade** | `PUT /admin/...`, `availability`, `staleAfter` real | publicar tarifa reflete na cotação; `staleAfter` avança com o TTL |
| **MB3 — Integração com o serviço de reservas** | trocar a fixture do `BrokerCatalog` pelo broker real | serviço de reservas cota e reserva contra o broker; validação de preço (VAL-0001) refeita com comparável real |
| **MB4 — Alocador (v2)** | `hold`/`bookings`/expiração | **sinal:** o comprador precisa reservar de verdade, não só cotar |

### 5.1 Não faremos até haver sinal

| Item | Sinal que autoriza |
|---|---|
| Alocador / allotment real | o comprador precisa confirmar reserva no broker |
| Agregador de fornecedores (fan-out, mapeamento) | existir o **segundo** fornecedor real |
| Multi-moeda | houver venda fora do BRL |
| Precificação dinâmica | existir regra comercial que mude preço por demanda |
| Multi-tenant / B2B ≠ B2C | existir o segundo buyer com portfólio distinto |
| Integração com PMS | houver hotel disposto a conectar |

---

## 6. Riscos

| # | Risco | Sev. | Mitigação |
|---|---|---|---|
| R1 | Vender tarifa velha como atual | **Alta** | `staleAfter` obrigatório + `updatedAt`; o comprador pode recusar |
| R2 | Divergência de mapeamento (quarto/regime) entre broker e comprador | Alta | IDs próprios no v1 + contrato explícito; `mapping` já modelado |
| R3 | **Seed virar "verdade"** e ninguém notar que é sintético | Alta | `data_source` no contrato — a resposta grita quando o dado é semeado |
| R4 | Disponibilidade publicada ≠ estoque real | Média | declarado informativo no v1; o comprador não deve vender com base nela |
| R5 | Premissa de buyer único | Média | API key desde o início; `rate_limit_per_min` já no modelo |
| R6 | `hold` com expiração traz deadlock/corrida | Média | só em MB4, com teste de concorrência como no serviço de reservas |
| R7 | Contrato publicado sem disciplina de versão | Média | `/v1` + apenas adição compatível; congelar em `docs/broker/` |
| R8 | Ingestão manual não escala além de ~10 hotéis | Baixa | aceitável no escopo; vira sinal para pull/PMS |

**Já coberto por decisão:** frescor explícito, erro tipado, idempotência de leitura, fronteira de segurança por API key, origem do dado declarada.

**Não coberto (consciente):** autenticação de usuário final, pagamento, cancelamento, SLA, backup/DR, notificação ao hotel.

---

## 7. Referências

- **Modelo de agregação e conectividade** — Travelgate `HotelX`: conexão única, códigos unificados de hotel/quarto/regime, divisão automática da busca conforme o limite de cada fornecedor, segmentação B2B/B2C e plugins de regra de negócio — [docs.travelgate.com](https://docs.travelgate.com/kb/connectivity-products/for-buyers/hotel-x/why-develop-hotel-x-explained/)
- **tutorial:30 — Seleção de API e comunicação entre serviços.** A fronteira vem antes do protocolo (síncrono/assíncrono, interno/externo); o contrato importa mais que o protocolo (entrada/saída, semântica de erro, idempotência, versionamento, limites, segurança); API externa pede estabilidade, não elegância. Base das decisões B2, B3 e B4.
- **ADR-002** do serviço de reservas — é o que a opção B (alocador) obriga a revisar.
- **[VAL-0001](../../.super-pm/decisions.pt-BR.md)** — a validação inconclusiva que motivou este projeto.

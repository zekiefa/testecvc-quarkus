---
project: testecvc-quarkus
role: booking-service          # lado do hóspede
doc_version: 1
updated: 2026-11-06
stack: Java 21 · Quarkus 3.40 · PostgreSQL 16 · Flyway
depends_on:
  - project: testecvc-broker-quarkus
    provides: catálogo, tarifas por data, cotação, disponibilidade publicada
    contract: openapi/broker-v1.yaml (v1)
    transport: REST/JSON · config BROKER_V1_URL (default http://localhost:8081)
owns: [reservas, inventário/estoque, taxa comercial, identidade do hóspede, snapshot de preço]
does_not_own: [catálogo de hotéis, tarifas por data, disponibilidade do fornecedor]
canonical:
  arquitetura: docs/arquitetura.md
  adrs: docs/adr/README.md
  decisoes_produto: .super-pm/decisions.pt-BR.md
---

# Capacidades de negócio — serviço de reservas

> **Leia o TL;DR antes de qualquer outra coisa.** Este arquivo é a fonte para agentes;
> cite IDs (`RES-01`, `R-03`) em vez de reescrever a capacidade.

## TL;DR

- Recebe o **hóspede**: autentica, mostra hotéis com preço, vende e gerencia a reserva.
- **Não é dono** do catálogo nem das tarifas: pede ao broker a cada consulta.
- **É dono** do estoque: só ele pode dizer se há vaga, e é ele que a bloqueia.
- Preço = tarifa **líquida de atacado** do broker × **1,1176** (taxa de 11,76%), aplicada **por noite**.
- Não existe pagamento, reembolso, e-mail, multi-moeda nem multi-tenant.

## Capacidades

| ID | Capacidade | Endpoint | Auth | Retorna |
|---|---|---|---|---|
| RES-01 | Login do hóspede | `POST /auth` | público | `{type, token}` JWT RS256, 24 h |
| RES-02 | Buscar hotéis na cidade por período | `GET /api/v1/booking/{cityCode}/{checkIn}/{checkOut}/{adults}/{children}` | `user` | 1 item **por hotel**: melhor plano do período, total com taxa, noites detalhadas, `dataSource` |
| RES-03 | Detalhe do hotel **para um período** | `GET /api/v1/hotels/{id}?checkIn&checkOut&adults&children` | `user` | quartos e regimes, cada um cotado (`available` true/false), `dataSource` |
| RES-04 | Disponibilidade no **nosso** estoque | `GET /api/v1/hotels/{id}/availability?checkIn&checkOut` | `user` | noites livres + mínimo do período |
| RES-05 | Criar reserva idempotente | `POST /api/v1/bookings` + header `Idempotency-Key` | `user` | `201` com `Location`; `200` no replay |
| RES-06 | Consultar reserva | `GET /api/v1/bookings/{id}` | `user` (dono) | reserva; **404** se de outro dono |
| RES-07 | Cancelar | `POST /api/v1/bookings/{id}/cancel` | `user` (dono) | devolve estoque |
| RES-08 | Check-in | `POST /api/v1/bookings/{id}/check-in` | `user` (dono) | a partir da data de entrada |
| RES-09 | Check-out | `POST /api/v1/bookings/{id}/check-out` | `user` (dono) | exige `CHECKED_IN` |
| RES-10 | No-show | `POST /api/v1/bookings/{id}/no-show` | `user` (dono) | após a data, sem check-in; **não** devolve estoque |
| RES-11 | Operação | `GET /q/openapi` · `/q/health` · `/q/metrics` | — | contrato, saúde, métricas |

Campos de `RES-05` (todos obrigatórios): `hotelId`, `roomId`, `ratePlanId`, `checkIn`, `checkOut`, `adults`, `children`.

## Invariantes de negócio

| ID | Regra | Violação |
|---|---|---|
| R-01 | Taxa de **11,76%** (1176 bps) sobre a tarifa **líquida**, por adulto e criança, aplicada **noite a noite** | configuração; fórmula em ADR-004 |
| R-02 | `diária = round(líquido × 1,1176, 2, HALF_UP)`; `total = Σ diárias × pax` | — |
| R-03 | **A tarifa recebida precisa ser líquida de atacado.** Preço de balcão faz o produto ficar ~11,76% acima do mercado, sem erro visível | risco R14 |
| R-04 | Preço é **congelado** no snapshot da reserva; mudança de tarifa não altera reserva confirmada | — |
| R-05 | **Idempotência**: mesma chave do mesmo dono → `200` com a reserva original; chave de outro dono → `409` | — |
| R-06 | Reserva de terceiro responde **404**, nunca 403 (403 confirmaria o id) | — |
| R-07 | **Anti-overbooking**: estoque reservado por noite via `UPDATE` condicional na mesma transação; sem linha afetada → `409` | — |
| R-08 | Estados: `CONFIRMED → CHECKED_IN → CHECKED_OUT`; `CONFIRMED → CANCELLED`; `CONFIRMED → NO_SHOW`. Fora do grafo → `409`; estados finais não reabrem | — |
| R-09 | `CANCELLED` devolve estoque; `NO_SHOW` **não** devolve | — |
| R-10 | Limites: período ≤ **30 noites**; `adults ≥ 1`; `children` 0–10; capacidade do quarto | `400` |
| R-11 | Cotação do broker precisa cobrir **todas** as noites e estar na nossa moeda (`booking.currency`) | `503` |
| R-12 | Cotação com `staleAfter` no passado é **recusada**; oferta de busca vencida é descartada | `503` / filtrada |
| R-13 | Todo erro é `application/problem+json` (RFC 7807) com `type` estável e `traceId` | — |

## Dependência: broker (`testecvc-broker-quarkus`)

| Uso | Chamada | Quando |
|---|---|---|
| Buscar hotéis | `GET /v1/search` | `RES-02` |
| Catálogo do hotel | `GET /v1/hotels/{id}` | `RES-03` |
| Cotar estadia | `POST /v1/quotes` | `RES-05`, `RES-03` |

**Campos que cruzam a fronteira** (contrato v1): `hotelId`, `roomTypeId`, `ratePlanId`, `checkIn`, `checkOut`, `adults`, `children`, `nights[]{date, adult, child}`, `currency`, `totalAmount`, `dataSource`, `updatedAt`, `staleAfter`.

> Documento equivalente do broker: <https://github.com/zekiefa/testecvc-broker-quarkus/blob/main/docs/ai/capabilities.md>
> Contrato OpenAPI (vive no repo do broker): <https://github.com/zekiefa/testecvc-broker-quarkus/blob/main/openapi/broker-v1.yaml>

**Tradução de erro (ACL)**: `404` do broker → nosso `404`; `409 incomplete-rate-period` → nosso `409 rate-unavailable`; cotação incompleta, moeda divergente ou `staleAfter` vencido → nosso `503`; transporte/5xx → `503`.

## Fronteira de dados

| Dado | Dono | Fonte |
|---|---|---|
| Reservas e eventos de reserva | **este serviço** | PostgreSQL (`V1`, `V2`) |
| Estoque por quarto/noite | **este serviço** | PostgreSQL (`V3`); capacidade padrão 1 por quarto/noite (premissa) |
| Usuários e papéis | **este serviço** | PostgreSQL (`V4`); senha só em bcrypt |
| Catálogo (hotel, quarto, regime) | broker | consultado a cada uso |
| Tarifa por data de estadia | broker | consultada a cada uso |

## Não-objetivos (não proponha sem decisão explícita)

Pagamento · reembolso · faturamento · notificação/e-mail · multi-moeda · multi-tenant · cancelamento parcial de diárias · rate limiting · integração com PMS · agente de IA (épico adiado).

## O que é real e o que é simulado

| Real | Simulado / ausente |
|---|---|
| Fluxo de reserva, preço, estoque, estados, idempotência, autorização por dono | Catálogo e tarifas do broker (`dataSource: SEED`, 10 hotéis de Serra Negra) |
| Taxa calibrada com pesquisa de mercado (VAL-0002) | Comissão de 15% do broker: **inferida**, não contratada |

## Como usar este arquivo (agentes)

1. Responda primeiro pelo **TL;DR** e pelas tabelas; só leia `docs/arquitetura.md` se a pergunta for de desenho.
2. Cite IDs (`RES-05`, `R-07`) na resposta — evita reescrever a regra e reduz tokens.
3. Antes de propor feature, confira **Não-objetivos**; antes de propor preço, leia `R-01..R-04` + ADR-004.
4. Para mudar valor de taxa ou contrato, a fonte é `.super-pm/decisions.pt-BR.md` e `docs/adr/`.

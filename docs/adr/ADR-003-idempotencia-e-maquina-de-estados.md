# ADR-003 — Idempotência por chave + máquina de estados explícita na reserva

| Campo | Valor |
|---|---|
| Status | Aceita |
| Data | 2026-10-28 |
| Decisores | Time de desenvolvimento |
| Relacionados | [ADR-001](ADR-001-monolito-modular.md) · [ADR-002](ADR-002-fonte-da-verdade.md) · [ADR-004](ADR-004-semantica-da-taxa.md) |
| Substitui | — |

## Contexto

A reserva é um compromisso com efeito duradouro sobre estoque (e, no futuro, sobre dinheiro). Clientes HTTP repetem requisições: timeout de rede, retry de SDK, duplo clique, refresh de página. Sem controle, cada repetição cria uma reserva nova e consome uma noite de estoque — o clássico "retry automático vira máquina de duplicar".

Além disso, o ciclo de vida (criada → check-in → check-out, com cancelamento e no-show) precisa ser explícito: hoje não existe estado algum, e regras como "check-in só a partir da data" não têm onde morar.

## Decisão

1. **`Idempotency-Key` obrigatório no `POST /api/v1/bookings`** (header, UUID gerado pelo cliente).
   - Persistida em `booking.idempotency_key` com `UNIQUE`.
   - Replay com a mesma chave devolve **a reserva existente** (`200`), sem produzir efeitos — inclusive se a primeira requisição tiver falhado depois do commit.
   - Janela de retenção: enquanto a reserva existir (sem expiração na fase atual).
2. **Máquina de estados explícita**, em `enum` de domínio, com tabela de transições permitidas:
   `CONFIRMED → CHECKED_IN → CHECKED_OUT`, `CONFIRMED → CANCELLED`, `CONFIRMED → NO_SHOW`.
   Transição não listada → **409 Conflict** com Problem Details. `PENDING` **não** é criado agora (entra junto com o fluxo de pagamento).
3. **Concorrência otimista** na reserva (`@Version`); conflito de versão → `409` (não `500`).
4. **Estoque reservado na mesma transação da reserva**, por `UPDATE` condicional por noite:
   `UPDATE room_inventory SET reserved = reserved + 1 WHERE hotel_id=? AND room_id=? AND stay_date=? AND reserved + 1 <= capacity`
   — linhas afetadas em **ordem crescente de data** (evita deadlock por ordem inconsistente). Qualquer noite sem linha afetada → rollback total + `409`.
5. **`booking_event` como log de auditoria append-only**, escrito **no mesmo commit**. Isto **não** é Outbox: não há publicação de mensagem nem consumidor nesta fase.
6. **Autorização por dono**: `GET`/`check-in`/`check-out`/`cancel` só para o `sub` do JWT que criou a reserva (além do papel `user`).

## Alternativas descartadas

| Alternativa | Por que foi descartada |
|---|---|
| **Idempotência por hash do payload** | Duas reservas legítimas e idênticas (mesmo quarto, mesmas datas, dois hóspedes) virariam uma só. A chave explícita separa intenção de repetição. |
| **Lock pessimista (`SELECT … FOR UPDATE`) no inventário** | Serializa todas as reservas do mesmo quarto, aumenta contenção e risco de deadlock; o `UPDATE` condicional resolve com uma linha de SQL. |
| **Confiar em "o cliente não repete"** | Retry é comportamento normal de rede, não erro do cliente. |
| **Outbox desde já** | Não há consumidor externo; adiciona tabela, poller e complexidade sem destinatário. Gatilho: existir consumo assíncrono. |
| **Estado implícito** (deduzir check-in da data) | Impossível distinguir no-show de check-in, e não há auditoria. |
| **Event sourcing da reserva** | Peso desproporcional: consulta difícil, evolução de schema difícil; um log de eventos append-only ao lado do estado atual já dá auditoria. |
| **`PENDING` na criação** | Sem pagamento, `PENDING` produziria reservas eternamente "meio-feitas" e exigiria saga de compensação para um passo que não existe. |

## Consequências

**Positivas**
- Retry seguro por construção; nenhuma duplicação de reserva ou de consumo de estoque.
- Overbooking impossível sob concorrência, garantido por constraint + `UPDATE` condicional.
- Toda transição é auditável com data e payload.
- Regras de check-in/check-out passam a ter lugar explícito no domínio.

**Negativas / custos aceitos**
- Cliente **precisa** gerar e reenviar a chave — contrato mais exigente (documentar no OpenAPI).
- Crescimento da tabela `booking` com chaves antigas (irrelevante nesta escala).
- Replay devolve `200` e não `201`: sutil, precisa estar documentado.
- Sem pagamento, `CONFIRMED` significa "reserva aceita", não "paga" — a semântica tem de ser dita.

## Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Mesma reserva criada com duas chaves distintas (duplo clique antes de gerar chave) | Chave gerada no início da submissão, no cliente; idempotência sozinha não cobre esse caso — documentar |
| Deadlock em reservas multi-noite | Ordenação determinística das datas |
| `409` virar `500` por exceção de lock não mapeada | Mapper explícito `OptimisticLockException`/`PessimisticLockException` → `409` |
| Evento e estado divergirem | Mesmo commit, mesma transação |

## Gatilhos de revisão

1. Existir consumidor assíncrono de eventos (e-mail, faturamento, antifraude) → avaliar **Outbox**.
2. Entrar pagamento → `PENDING` + saga com compensação (novo ADR).
3. Volume de escrita tornar o `UPDATE` condicional gargalo → avaliar particionamento/contadores agregados.
4. Necessidade de reserva multi-quarto numa única transação.

## Plano de saída

A chave fica numa coluna isolada com índice único: dá para migrar para um *gateway* com idempotência nativa (ex.: API Gateway) sem mudar o domínio. O log de eventos é independente do estado atual, então uma futura migração para Outbox/CDC é aditiva (adicionar tabela de saída e reconciliar), não destrutiva.

## Referências

- tutorial:11 — idempotência como base da correção distribuída: chave + deduplicação; "retry e idempotência andam juntos"; Outbox só quando há entrega de mensagem; CQRS/ES não se justificam por si.
- tutorial:19 — separar por risco: ações de baixa frequência e alto impacto merecem caminho e validação próprios.

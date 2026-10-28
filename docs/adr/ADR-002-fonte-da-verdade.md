# ADR-002 — O broker é catálogo e tarifa; reservas e inventário são a fonte da verdade local

| Campo | Valor |
|---|---|
| Status | Aceita |
| Data | 2026-10-28 |
| Decisores | Time de desenvolvimento |
| Relacionados | [ADR-001](ADR-001-monolito-modular.md) · [ADR-003](ADR-003-idempotencia-e-maquina-de-estados.md) |
| Substitui | — |

## Contexto

Hoje toda a informação de hotel vem de um broker externo por REST (`/cities/{code}/hotels`, `/hotels/{id}`) e é convertida em objetos de domínio **dentro** do `BookingService`. Verificado no código e confirmado pelo time: **o broker não expõe estoque nem disponibilidade** — só catálogo e tarifas (adulto/criança por quarto).

Consequência direta: sem estoque próprio, "disponibilidade" não existe, e a criação de reserva não teria como impedir overbooking. Também não sabemos se o broker é idempotente, se tem SLA, nem quando muda de contrato.

## Decisão

1. **Separação de propriedade de dados:**
   - **Broker** é dono do **catálogo e das tarifas** — leitura, volátil, sem SLA.
   - **Nós** somos donos das **reservas** e do **inventário que vendemos** — durável, transacional.
2. **Disponibilidade é calculada localmente:** `capacidade(quarto, noite) − Σ reservas ativas`. Fonte: tabela `room_inventory`.
3. **Materialização sob demanda** do inventário: na primeira reserva de um quarto, as noites solicitadas recebem `capacity` padrão **1** (parametrizável). Sincronização em massa com o broker não faz parte desta decisão.
4. **Porta + camada anticorrupção:** `BrokerClient` (interface do domínio) e `BrokerClientAdapter` (REST Client + mapeamento `BrokerHotel` → `Hotel`). Nenhuma classe de `booking`/`inventory`/`catalog` referencia DTOs do broker.
5. **Falha do broker é falha declarada:** timeout curto, `503` com Problem Details. Nunca preencher preço/estoque com valor inventado ou *stale* silencioso.
6. **Preço congelado na reserva:** o `booking` guarda o snapshot (`nightly_breakdown`, `total_amount`); mudança de tarifa no broker não altera reserva confirmada.

## Alternativas descartadas

| Alternativa | Por que foi descartada |
|---|---|
| **Delegar disponibilidade ao broker** | Ele não expõe estoque. Não é uma escolha — é impossível hoje. |
| **Cache local do catálogo (TTL) já agora** | Sem sinal de degradação (p99 do broker OK) e adiciona invalidação + dado stale. Fica como gatilho em M3. |
| **Tratar o broker como fonte da verdade de disponibilidade quando ele evoluir** | Vira decisão nova (ADR) no momento em que existir contrato de estoque; hoje seria especulação. |
| **Inventário embutido na tabela `booking`** (só contar reservas) | Perde capacidade, ajustes manuais e bloqueios (manutenção); e a checagem de concorrência fica impossível de expressar como `UPDATE` condicional. |
| **Sincronizar inventário em lote com o broker agora** | Exige contrato que não temos; o upsert sob demanda entrega o mesmo efeito para o MVP. |

## Consequências

**Positivas**
- Overbooking passa a ser impossível por construção (constraint + `UPDATE` condicional na mesma transação).
- Reservas sobrevivem a indisponibilidade do broker (leitura degrada, escrita falha com `503` explícito).
- Trocar de broker/PMS mexe em um único adaptador.
- Preço da reserva é imutável e auditável.

**Negativas / custos aceitos**
- Assumimos responsabilidade operacional pelo estoque: **se o broker vender por outro canal, não saberemos** (risco R4/overbooking externo).
- `capacity = 1` por quarto/noite é premissa nossa, não dado do broker.
- Catálogo e inventário podem divergir quando o broker remover um hotel; reservas antigas continuam válidas (proposital).

## Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Venda em canal paralelo gera overbooking real | Declarar limitação; futuro: contrato de estoque com o broker (novo ADR) |
| `capacity` errada | Parametrizar por `(hotel, quarto)` e permitir ajuste administrativo em M2 |
| Divergência catálogo × reservas | Reserva guarda snapshot de hotel/quarto/nome; exibição tolera hotel ausente no catálogo |

## Gatilhos de revisão

1. O broker passar a expor **estoque/disponibilidade** com contrato estável.
2. Existir mais de um canal de venda (aí o inventário precisa de coordenação entre canais).
3. Necessidade de bloqueio de manutenção/indisponibilidade programada de quarto.
4. p99 de leitura do broker degradar ponto de exigir cache com invalidação.

## Plano de saída

`BrokerClient` é a fronteira: trocar broker, adicionar cache ou plugar um PMS real é substituir o adaptador, sem tocar em `booking`/`inventory`. Se o estoque migrar para o broker, o caminho é *expand-contract*: espelhar o estoque remoto em `room_inventory`, comparar por reconciliação, migrar as escritas e só então aposentar a tabela local.

## Referências

- tutorial:34 — antes de escolher ferramenta, localizar o modo de falha (aqui: dado errado/estoque).
- tutorial:11 — consistência entre fronteiras: manter a fonte da verdade explícita e reconciliar, em vez de assumir consistência que a rede não garante.

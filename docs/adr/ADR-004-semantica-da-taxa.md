# ADR-004 — Semântica da taxa em basis points e extração do `PricingCalculator`

| Campo | Valor |
|---|---|
| Status | Aceita — **valor da taxa em aberto** (7% é apenas valor provisório de teste local) |
| Data | 2026-10-28 |
| Decisores | Time de desenvolvimento |
| Relacionados | [ADR-001](ADR-001-monolito-modular.md) · [ADR-003](ADR-003-idempotencia-e-maquina-de-estados.md) |
| Substitui | — |

## Contexto

O cálculo de preço atual está em `BookingService` e produz **dois números que não fecham**:

- `priceDetail.pricePerDayAdult` **inclui** a taxa: `(tarifa + taxa) × 1 dia` — [BookingService.java:64](../../src/main/java/br/com/cvc/evaluation/service/BookingService.java#L64)
- `room.totalPrice` **exclui** a taxa: `tarifa × dias` — [BookingService.java:65](../../src/main/java/br/com/cvc/evaluation/service/BookingService.java#L65)

Exemplo com adulto 900, criança 450 e 3 noites: o detalhe mostra 1.530/dia (com taxa) enquanto o total exibe 4.050 (sem taxa). Além disso, `calculateTotalPrice` ([BookingService.java:43](../../src/main/java/br/com/cvc/evaluation/service/BookingService.java#L43)) soma a taxa de **um** dia e multiplica o conjunto pelos dias — se fosse chamada com `days > 1`, a taxa seria aplicada duas vezes.

A configuração `booking.fee: 0.70` ([application.yml:7](../../src/main/resources/application.yml#L7)) é ambígua: 70% de markup é comercialmente implausível como regra de negócio. O time confirmou que o valor atual é **provisório, apenas para teste local** — não existe número comercial definido. **Nenhum teste fixa valor esperado** — os testes mockam `FeeService`, então o erro é invisível.

O cálculo é dinheiro: precisa ser determinístico, testável sem infraestrutura e impossível de "quase certo".

## Decisão

1. **Extrair `PricingCalculator`** para o módulo `pricing`: classe **pura** (sem CDI, sem I/O, sem banco), com entrada e saída explícitas.
2. **Regra única e documentada:**
   - `diáriaPorPax = round(tarifaPax × (1 + taxa), 2, HALF_UP)`
   - `totalReserva = Σ_diárias (diáriaAdulto × adultos + diáriaCriança × crianças) × noites`
   - **Invariante:** `totalReserva == Σ nightly_breakdown`, verificado em teste e (M2) em produção como asserção de sanidade.
3. **Taxa configurada em basis points**, não em fração: `booking.fee-bps: 700` = **7,00%**. Esta decisão fixa a **unidade** (bps), não o número: o valor comercial segue em aberto e `700` é só o valor provisório de teste local. Elimina a ambiguidade de `0.70` (70% vs. 0,70%).
4. **Arredondamento explícito**: `HALF_UP`, escala 2, moeda `BRL`; arredondar **por diária por pax** e então multiplicar, nunca arredondar o total no fim (evita divergência de centavo entre detalhe e total).
5. **Snapshot na reserva**: `total_amount` + `nightly_breakdown` (JSONB) gravados na criação; recálculo posterior usa o snapshot, não a tarifa vigente.
6. **Testes obrigatórios**: 1 noite, 3 noites, adultos/crianças combinados, tarifa com dízima, e o caso do exemplo acima (900/450, 3 noites) com valor esperado escrito à mão.

> ⚠️ **Pendência (não bloqueia M0 nem M1):** o **valor comercial** da taxa e sua incidência (adulto e/ou criança) ainda não foram definidos. `7%` é apenas valor provisório de teste local, marcado no código como `TODO(PRODUTO)`. Consequências práticas:
> (a) `fee-bps` fica parametrizado **por ambiente**, nunca embutido no código;
> (b) nenhum teste trata `7%` como regra de negócio — o teste fixa a **fórmula**, com o valor declarado como fixture explícita;
> (c) definir o número depois é mudança de configuração, não de código.

## Alternativas descartadas

| Alternativa | Por que foi descartada |
|---|---|
| **Manter `0.70` como markup de 70%** | Inviável comercialmente e sem confirmação; manteria o total inconsistente. |
| **Corrigir só o bug, mantendo o cálculo no `BookingService`** | O service depende de broker e CDI; sem isolamento, o teste continua mockando `FeeService` e o valor real nunca é verificado. |
| **Taxa como `double`** | Erro de representação em dinheiro. `BigDecimal` com escala e modo de arredondamento definidos. |
| **Arredondar apenas o total** | Gera `total ≠ Σ diárias` no extrato — exatamente a classe de bug que este ADR elimina. |
| **Taxa por canal/temporada já agora** | Sem requisito; a porta `FeePolicy` deixa o caminho aberto. |
| **Impostos e taxas municipais no mesmo cálculo** | Escopo diferente (fiscal), entra como componente separado do breakdown quando houver requisito. |

## Consequências

**Positivas**
- Preço passa a ser testável por valor, sem broker, sem banco, rápido.
- Detalhe e total fecham por construção (invariante testada).
- Mudança de taxa vira configuração, não código.
- Reservas ficam imunes a alteração de tarifa no broker.

**Negativas / custos aceitos**
- **Mudança de contrato de resposta**: `totalPrice` deixa de ser "sem taxa" — consumidores veriam valores diferentes (hoje não há consumidores externos).
- Necessidade de definir o valor comercial da taxa antes de produção (a pendência acima).
- `nightly_breakdown` em JSONB adiciona flexibilidade com menos tipagem garantida pelo banco.

## Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Valor da taxa errado em produção | `fee-bps` por ambiente + `TODO(PRODUTO)` + teste da fórmula (não do número) |
| Divergência detalhe × total | Invariante `total == Σ breakdown` testada e verificada em runtime (M2) |
| Rounding diferente entre criação e exibição | Cálculo único no `pricing`; exibição não recalcula |
| Mudança de moeda | `currency` explícito na reserva; multi-moeda exige ADR novo |

## Gatilhos de revisão

1. Confirmação do valor/incidência da taxa pelo produto → fechar a pendência (versão v0.2 deste ADR).
2. Necessidade de taxa por canal, temporada ou tipo de tarifa → introduzir `FeePolicy` como estratégia.
3. Entrada de impostos/taxas municipais no breakdown.
4. Multi-moeda.

## Plano de saída

O cálculo vive atrás de uma função pura num único módulo: trocar por tabela de tarifas, política externa ou serviço de pricing é substituir a implementação de `FeePolicy`, mantendo o mesmo `nightly_breakdown` como contrato de dados. Reservas antigas preservam o snapshot.

## Referências

- tutorial:34 — seleção/ajuste de tecnologia guiado por modo de falha; aqui o modo de falha é **dinheiro errado**, e a resposta é isolamento + teste de valor.
- tutorial:11 — não confiar em consistência implícita: a invariante tem de ser verificada, não presumida.

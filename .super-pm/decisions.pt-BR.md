# Registro de Decisões de Produto — versão em português

> **Este arquivo é uma tradução para leitura humana.**
> A fonte que a ferramenta Super PM lê e escreve é [`decisions.md`](decisions.md), cujos
> **rótulos de campo são gerados pelo plugin** (em chinês) e são estruturais: traduzi-los
> faz o parser perder os campos. Os **valores** são escritos em português e estão aqui
> traduzidos junto com os rótulos.

---

## DEC-0002 — Qual a taxa de serviço comercial aplicada sobre a tarifa da reserva? · **vigente**

| Campo | Valor |
|---|---|
| ID | `DEC-0002` |
| Status | **vigente** — substitui a `DEC-0001` |
| Data | 2026-11-06 |
| Responsável | produto |
| Motivo da mudança | a coleta com hotel comparável (VAL-0002) mostrou que 5% deixava o preço 10,75% **abaixo** do mercado |

**Decisão.** Aplicar **11,76% (1176 bps)** de markup ao hóspede sobre a **tarifa líquida de
atacado**, incidindo sobre adulto e criança, fixo para todas as reservas e configurável em
`booking.fee-bps`. O valor é o que mantém ~5% de vantagem sobre o mercado com comissão de 15%.

**Limite negativo.** Nunca aplicar taxa sobre valor já taxado (bitributação); nunca taxa
negativa; nunca taxa zero em produção por omissão; **nunca assumir preço de balcão como líquido
de atacado** (risco R14).

**Evidência.** VAL-0002 (Hotel Fazenda Vale da Cachoeira, 3 sites, 10–13/04/2027): spread de
apenas 5,01% entre sites para o mesmo hotel; com 5% o nosso preço ficava 10,75% abaixo do
mercado. O desvio depende só de comissão e taxa: `delta = (1 − comissão) × (1 + taxa) − 1`.

**Próxima validação.** Obter a comissão **real** praticada pelo nosso broker para o hotel
coletado e recalcular o desvio; se sair de ±10%, revisar a taxa.

**Gatilho de revisão.** Comissão real do broker diferente de 15%; dados de conversão das
primeiras reservas; ou necessidade de preço/desconto por canal ou temporada (tabela de preços).

---

## DEC-0001 — mesma pergunta · **substituída pela DEC-0002**

| Campo | Valor |
|---|---|
| ID | `DEC-0001` |
| Status | substituída em 2026-11-06 |
| Data | 2026-11-03 |
| Decisão original | 5% (500 bps) sobre a tarifa |
| Por que caiu | conservador demais: 10,75% abaixo do mercado |

---

## Validações

### VAL-0002 — coleta real com hotel comparável · 2026-11-06

| Campo | Valor |
|---|---|
| Hipótese | 5% mantém o preço dentro de 10% do mercado, para o mesmo hotel e datas |
| Método | Hotel Fazenda Vale da Cachoeira, Serra Negra · 10→13/04/2027 · 1a+1c · 3 noites · coleta em 05/11/2026 · líquido **inferido** pela comissão de cada site (`líquido = bruto × (1 − comissão)`) |
| Amostra | Booking 2.600,00 · Decolar 2.478,00 · Hoteis.com 2.476,00 |
| Limiar | desvio acima de 10% reabre a decisão |
| Observado | spread de **5,01%**; nosso preço com 5% = 2.209,83 (**−10,75%**) |
| Interpretação | fora da faixa **na direção favorável** — a taxa estava conservadora; o instrumento agora mede (antes: 167% de dispersão comparando hotéis diferentes) |
| Ação | taxa elevada para **11,76%** → desvio ≈ **−5,00%** (DEC-0002) |
| Efeito na decisão | **alterada** |

#### Taxas de referência (comissão 15%)

| Taxa | Desvio | Leitura |
|---|---|---|
| 5% | −10,75% | mais barato que o necessário |
| 5,88% | −10,00% | limite da faixa |
| **11,76%** | **−5,00%** | **posição atual** |
| 17,65% | 0% | paridade com o mercado |

### VAL-0001 — cotação agregada de Serra Negra · 2026-11-04

| Campo | Valor |
|---|---|
| Hipótese | 5% mantém o preço final dentro de 10% do mercado |
| Método | média dos top-3 de Airbnb, Booking e Trivago — **produtos não equivalentes**, por noite |
| Observado | R$ 712 / 1.190 / 1.904 por noite — spread de 167% |
| Interpretação | dispersão entre sites ~50× a diferença entre 5% e 7%; sem comparável no broker da época |
| Efeito na decisão | mantida (na ocasião) |

---

## Como isso se relaciona com os ADRs

| Decisão | ADR correspondente |
|---|---|
| DEC-0002 — taxa de 11,76% (vigente) | [ADR-004](../docs/adr/ADR-004-semantica-da-taxa.md) |
| DEC-0001 — taxa de 5% (substituída) | [ADR-004](../docs/adr/ADR-004-semantica-da-taxa.md) |

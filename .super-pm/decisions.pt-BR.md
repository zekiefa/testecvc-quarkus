# Registro de Decisões de Produto — versão em português

> **Este arquivo é uma tradução para leitura humana.**
> A fonte que a ferramenta Super PM lê e escreve é [`decisions.md`](decisions.md), cujos
> **rótulos de campo são gerados pelo plugin** (em chinês) e são estruturais: traduzi-los
> faz o parser perder os campos. Os **valores** são escritos em português e estão aqui
> traduzidos junto com os rótulos.

---

## DEC-0001 — Qual a taxa de serviço comercial aplicada sobre a tarifa da reserva?

| Campo | Valor |
|---|---|
| ID | `DEC-0001` |
| Status | vigente |
| Data | 2026-11-03 |
| Responsável | produto |

**Decisão.** Aplicar **5% (500 bps)** sobre a tarifa base do broker, incidindo sobre adulto
e criança, como markup embutido no preço final. Fixa para todas as reservas e configurável
por ambiente em `booking.fee-bps`.

**Limite negativo.** Nunca aplicar taxa sobre valor já taxado (bitributação); nunca taxa
negativa; nunca taxa zero em produção por omissão.

**Evidência.** Decisão de posicionamento: 5% mantém o preço final próximo da tarifa do
broker e ainda cobre o custo de intermediação. Ainda sem cotação de concorrentes na época da
decisão — a tentativa posterior está em VAL-0001.

**A validar.** Cotação agregada tentada em [VAL-0001](validations.md); o critério original não se mostrou aplicável.

**Próxima validação.** Revisar a taxa quando existir **broker real com inventário comparável**
ou quando houver **dados de conversão** das primeiras reservas. A cotação de concorrentes
(VAL-0001) não permitiu aplicar o critério de 10%.

**Validações.** VAL-0001 — cotação agregada de Serra Negra: inconclusiva (ver seção
[Validações](#validações)).

**Gatilho de revisão.** Margem insuficiente, renegociação com parceiro, ou necessidade de
taxa por canal/temporada.

**Substitui / substituída por / motivo da mudança.** Não se aplica — decisão original.

---

## Como isso se relaciona com os ADRs

Os ADRs em [`docs/adr/`](../docs/adr/) já são escritos em português e são o documento
técnico de referência. Este registro é o lado de **produto**: registra a decisão, a
evidência disponível, o limite negativo e o gatilho que a reabre.

| Decisão | ADR correspondente |
|---|---|
| DEC-0001 — taxa de 5% | [ADR-004](../docs/adr/ADR-004-semantica-da-taxa.md) |

---

## Validações

### VAL-0001 — cotação agregada de Serra Negra (inconclusiva)

| Campo | Valor |
|---|---|
| Data | 2026-11-04 |
| Decisão | DEC-0001 |
| Hipótese | 5% mantém o preço final dentro de 10% do mercado |
| Método | Média dos top-3 de Airbnb, Booking e Trivago — Serra Negra, 1a+1c, 10→13/04/2027, **por noite** |
| Limiar | diferença > 10% reabre a decisão |
| Observado | R$ 712 / R$ 1.190 / R$ 1.904 por noite; spread de 167% |
| Interpretação | a dispersão entre sites (~R$ 1.192/noite) é ~50x a diferença entre 5% e 7% (R$ 23,80/noite); produtos não equivalentes e sem comparável no broker |
| Próxima ação | trocar o critério: broker real comparável ou dados de conversão |
| Efeito na decisão | **mantida** (5%) |

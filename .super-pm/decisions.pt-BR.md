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
broker e ainda cobre o custo de intermediação. Ainda sem cotação de concorrentes — essa
lacuna é a próxima validação.

**A validar.** Cotação de concorrentes pendente.

**Próxima validação.** Cotar 3 reservas equivalentes em concorrente e comparar o preço
final; diferença acima de 10% reabre a decisão.

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

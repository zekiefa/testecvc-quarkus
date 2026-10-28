# Registros de Decisão de Arquitetura (ADRs)

Decisões que moldam a evolução desta API de reservas. Cada ADR registra **o que** foi decidido, **o que foi descartado e por quê**, o custo aceito e **quando rever** — porque a decisão certa muda quando as restrições mudam.

| # | Decisão | Status | Data |
|---|---|---|---|
| [ADR-001](ADR-001-monolito-modular.md) | Monólito modular com PostgreSQL e Flyway (sem microsserviços) | Aceita | 2026-10-28 |
| [ADR-002](ADR-002-fonte-da-verdade.md) | Broker é catálogo/tarifa; reservas e inventário são a fonte da verdade local | Aceita | 2026-10-28 |
| [ADR-003](ADR-003-idempotencia-e-maquina-de-estados.md) | Idempotência por chave + máquina de estados explícita na reserva | Aceita | 2026-10-28 |
| [ADR-004](ADR-004-semantica-da-taxa.md) | Semântica da taxa em basis points e extração do `PricingCalculator` | Aceita (valor em aberto) | 2026-10-28 |

## Como usar

- **Antes de mudar uma dessas decisões**, leia o "Gatilho de revisão" e o "Plano de saída" do ADR correspondente.
- **Toda decisão estrutural nova** (pagamento, multi-tenant, mensageria, IA) vira um novo ADR numerado sequencialmente.
- **Não se reescreve um ADR aceito.** Para mudar, cria-se um novo ADR que o substitui e anota-se "Substituído por" no antigo.
- Documento de arquitetura vigente: [../arquitetura.md](../arquitetura.md).

## Modelo

Contexto → Decisão → Alternativas descartadas (com o motivo) → Consequências → Riscos → Gatilhos de revisão → Plano de saída → Referências.

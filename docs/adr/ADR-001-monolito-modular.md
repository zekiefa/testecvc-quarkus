# ADR-001 — Monólito modular com PostgreSQL e Flyway (sem microsserviços)

| Campo | Valor |
|---|---|
| Status | Aceita |
| Data | 2026-10-28 |
| Decisores | Time de desenvolvimento |
| Relacionados | [ADR-002](ADR-002-fonte-da-verdade.md) · [ADR-003](ADR-003-idempotencia-e-maquina-de-estados.md) · [ADR-004](ADR-004-semantica-da-taxa.md) |
| Substitui | — |

## Contexto

A aplicação é hoje um único processo Quarkus (3.12.3, Java 21) com 27 classes de produção, sem estado persistente: catálogo e tarifas vêm de um broker externo, e "reserva" é apenas cálculo de preço. O produto a ser entregue exige reservas duráveis, controle de disponibilidade e transições de estado (check-in/check-out) — ou seja, exige **transação, concorrência e dados que sobrevivem ao reinício**.

Restrições relevantes: equipe de 1 pessoa; finalidade de estudo; o modo de falha dominante é **dado errado** (overbooking, total inconsistente), não throughput; o broker não é controlado por nós (ADR-002).

## Decisão

1. **Um único artefato implantável** (o app Quarkus atual), organizado em **módulos por feature** dentro do mesmo pacote raiz: `identity`, `catalog`, `pricing`, `inventory`, `booking`, `shared`.
2. **Um único banco relacional: PostgreSQL**, acessado por Hibernate ORM com Panache; esquema versionado por **Flyway** (`src/main/resources/db/migration`).
3. **PostgreSQL também em desenvolvimento e teste**, via **Dev Services** do Quarkus (container iniciado automaticamente); sem H2, sem banco em memória, sem "perfil diferente de banco".
4. Fronteiras garantidas por **regra de dependência entre pacotes** (documentada em `docs/arquitetura.md`, §3.2), verificável por revisão agora e por ArchUnit em M3.
5. Integrações externas **sempre atrás de uma porta** (`BrokerClient`, `UserRepository`), com o adaptador na borda.

## Alternativas descartadas

| Alternativa | Por que foi descartada |
|---|---|
| **Continuar stateless** (delegar reserva e estoque ao broker) | O broker não expõe estoque; sem estado não existe reserva, check-in ou check-out. Viola o produto. |
| **Microsserviços** (`catalog` / `booking` / `payment` / `inventory` separados) | Para 1 pessoa e fase de estudo, multiplica o custo operacional (N deploys, N bancos, contratos, tracing) sem nenhum ganho mensurável. Exigiria saga distribuída para uma transação que um `BEGIN…COMMIT` resolve. |
| **Multi-módulo Maven agora** (módulos como artefatos separados) | Boundary drift é resolvido depois, com ArchUnit; criar 6 `pom.xml` antes de existir regra de negócio é cerimônia sem retorno. |
| **H2 / banco em memória em dev** | Esconde diferenças reais de tipos (`jsonb`, `numeric`, constraints, locking) e produz "funciona no meu H2". |
| **Sem Flyway** (`hibernate.hbm2ddl.update`) | Esquema sem histórico nem revisão; impossível reproduzir ambiente ou evoluir com segurança. |

## Consequências

**Positivas**
- Transações ACID reais cobrem criar reserva + reservar estoque + registrar evento numa única unidade atômica.
- Um deploy, um banco, um log: depuração e operação triviais para uma pessoa.
- Postgres em dev elimina a classe de bug "passou local, quebrou no ambiente real".
- Extrair um módulo depois é mover um pacote + criar um adaptador HTTP — o custo é conhecido e baixo.

**Negativas / custos aceitos**
- Escala e cadência de deploy são únicas para todos os módulos.
- Fronteiras são **convenção**, não enforcement de build (risco R6).
- Um pool de conexões e um banco viram ponto único de falha.
- Dev passa a exigir Docker rodando (Dev Services).

## Riscos e mitigação

| Risco | Mitigação |
|---|---|
| *Boundary drift*: `catalog` passar a depender de `booking` | Regra explícita na doc + ArchUnit em M3 |
| Dev Services indisponível (sem Docker) | Documentar no README; alternativa manual = Postgres local via `docker compose` |
| `hbm2ddl` acidental em produção | `quarkus.hibernate-orm.database.generation=none` fixado em config |

## Gatilhos de revisão

Revisar esta decisão se **qualquer** condição ocorrer:

1. Um módulo precisar de **escala ou cadência de deploy** genuinamente distinta dos demais.
2. O time passar de 1 para ≥ 3 pessoas **e** houver disputa real de propriedade de código.
3. Latência de escrita passar de 600 ms p99 por contenção no banco.
4. Aparecer necessidade de stack tecnológica incompatível (ex.: GPU para IA) no mesmo runtime.

## Plano de saída

Não há lock-in relevante: JPA/Flyway são padrões; o esquema é SQL puro e portável. Se um módulo for extraído, o caminho é (a) mover o pacote para um novo artefato, (b) substituir a chamada intra-JVM por HTTP/gRPC no adaptador, (c) manter o mesmo banco ou migrar por *expand-contract* (adicionar, duplicar, migrar leitura, remover).

## Referências

- tutorial:34 — árvore de decisão de escolha tecnológica: primeiro "preciso mesmo de tecnologia nova?"; estágio do produto define o número de componentes; ADR precisa de alternativa, gatilho e plano de saída.
- tutorial:11 — engenharia de consistência: transações locais são a base; saga só quando a transação é cortada por rede.

# Validação da taxa comercial (DEC-0001)

> **O que está sendo testado:** a hipótese de que **5% de markup** mantém o preço final
> dentro de 10% do que o hóspede pagaria no mercado, para o **mesmo hotel e as mesmas datas**.
> A decisão e o critério estão na [DEC-0001](../../.super-pm/decisions.pt-BR.md).

## Por que a primeira tentativa não valeu

A tentativa anterior comparou a nossa cotação com a **média dos top-3** de três sites para
Serra Negra, sem hotel em comum: R$ 712, R$ 1.190 e R$ 1.904 por noite — 167% de dispersão,
isto é, ~50× a diferença entre 5% e 7%. O instrumento tinha erro maior que o efeito a medir.

Agora existe infraestrutura para fazer diferente: o broker publica tarifa **por data** e aceita
publicação por PUSH. O que falta é **dado real**, não código.

## O que coletar (2 a 3 hotéis)

Para cada hotel, **um** registro:

| Campo | Como obter |
|---|---|
| Hotel e quarto | o mesmo produto nos dois lados — mesmo hotel, mesma categoria |
| Datas | idênticas às da comparação (ex.: 10→13/04/2027, 3 noites) |
| Ocupação | 1 adulto + 1 criança |
| **Preço público total** | o que o hóspede paga no concorrente, **com impostos e taxas**, sem login e sem cupom |
| Política de cancelamento | compare flexível com flexível |
| Fonte e data da coleta | preço de hotel muda por semana — sem data, o número não vale |

E a tarifa **líquida** que o hotel nos cobra (o `netAdult`/`netChild`). Se não houver tarifa
líquida real, a comparação vira simulação — e deve ser declarada como tal.

## Como rodar

```shell
# 1. broker no ar (porta 8081) e serviço de reservas (8080)
# 2. preencha o CSV
cat > cotacoes.csv <<'CSV'
hotelId,roomId,ratePlanId,checkIn,checkOut,adults,children,netAdult,netChild,marketPrice,source,quotedAt
1,1,1,2027-04-10,2027-04-13,1,1,900.00,450.00,4252.50,booking.com,2026-11-05
CSV

./tools/validar-taxa.sh cotacoes.csv
```

O script, por linha:

1. publica a tarifa líquida no broker (`PUT /admin/rooms/{id}/rates`, origem **PUSH**);
2. cria uma reserva de verdade e lê o `totalAmount` — o preço que o hóspede pagaria de fato;
3. **cancela** a reserva, devolvendo o estoque;
4. calcula o desvio contra o preço público e imprime a tabela.

`--dry-run` calcula pela regra do ADR-004 sem chamar os serviços — útil para conferir a conta,
**não** é validação.

## Leitura do resultado

| Resultado | Leitura | Ação |
|---|---|---|
| todos os desvios ≤ 10% | 5% é competitivo | manter; fecha a validação |
| estamos mais baratos > 10% | há espaço de preço | nova decisão sobre subir a taxa |
| estamos mais caros > 10% | perda de competitividade | nova decisão sobre reduzir, ou manter com justificativa |
| sem hotel comparável | o método não se aplica | declarar a limitação; critério novo |

O veredito do script é **insumo**, não decisão: quem decide é a DEC-0001, com o resultado
registrado via Super PM (`record_validation`).

## Limites declarados

- 2–3 cotações são um **teste de sanidade**, não pesquisa de preço;
- tarifa líquida inventada transforma a comparação em simulação — e isso precisa estar escrito;
- uma OTA pode comprar mais barato que o nosso broker; a comparação mede **preço final ao
  hóspede**, não a nossa eficiência de compra.

## DEC-0001: Qual a taxa de serviço comercial aplicada sobre a tarifa da reserva?
<!-- dcid: DEC-0001; key: f2dcf02afd7abb9b1e6fa5fb233b1459202f2f9d45bb592a9308de2b0fef90b2; status: superseded; created: 2026-11-03 -->
- Status: substituída
- Data: 2026-11-03
- Decisão: Aplicar 5% (500 bps) sobre a tarifa base do broker, incidindo sobre adulto e criança, como markup embutido no preço final. Fixa para todas as reservas e configurável por ambiente em booking.fee-bps.
- Fronteira Negativa: Nunca aplicar taxa sobre valor já taxado (bitributação); nunca taxa negativa; nunca taxa zero em produção por omissão.
- Evidência: Decisão de posicionamento: 5% mantém o preço final próximo da tarifa do broker e ainda cobre o custo de intermediação. Ainda sem cotação de concorrentes na época da decisão (a tentativa posterior está em VAL-0001).
- A validar: mecanismo pronto (tools/validar-taxa.sh + broker com PUSH); falta publicar tarifas reais de 2-3 hoteis
- Próxima validação: Publicar a tarifa liquida real de 2-3 hoteis de Serra Negra via PUT /admin/rooms/{id}/rates (PUSH), rodar tools/validar-taxa.sh e comparar o preco final com o publico do mesmo hotel/datas; desvio > 10% reabre a decisao.
- Gatilho de revisão: Margem insuficiente, renegociação com parceiro, ou necessidade de taxa por canal/temporada.
- Responsável: produto
- Substitui: Nenhuma
- Substituída por: DEC-0002
- Motivo da mudança: A coleta real com hotel comparável (VAL-0002) mostrou que 5% deixava o preço 10,75% abaixo do mercado — fora da faixa de tolerância por ser conservador demais. 11,76% é o valor que mantém ~5% de vantagem com comissão de 15%, preservando competitividade e capturando margem.

## DEC-0002: Qual a taxa de serviço comercial aplicada sobre a tarifa da reserva?
<!-- dcid: DEC-0002; key: f2dcf02afd7abb9b1e6fa5fb233b1459202f2f9d45bb592a9308de2b0fef90b2; status: current; created: 2026-11-06 -->
- Status: atual
- Data: 2026-11-06
- Decisão: Aplicar 11,76% (1176 bps) de markup ao hóspede sobre a tarifa líquida de atacado, incidindo sobre adulto e criança, fixo para todas as reservas e configurável em booking.fee-bps. Valor escolhido para ficar ~5% abaixo do mercado com comissão de 15%.
- Fronteira Negativa: Nunca aplicar taxa sobre valor já taxado (bitributação); nunca taxa negativa; nunca taxa zero em produção por omissão; nunca assumir preço de balcão como líquido de atacado (risco R14).
- Evidência: VAL-0002 (Hotel Fazenda Vale da Cachoeira, 3 sites, 10-13/04/2027): spread de 5,01% entre sites para o mesmo hotel; com 5% o nosso preço ficava 10,75% abaixo do mercado. delta = (1 - comissao) x (1 + taxa) - 1: com comissao 15%, taxa 11,76% produz delta ~-5,00%; taxa 5,88% produz -10%; 17,65% produz paridade.
- A validar: Pendente
- Próxima validação: Obter a comissão real praticada pelo nosso broker para o hotel coletado e recalcular o desvio; se sair de ±10%, revisar a taxa.
- Gatilho de revisão: Comissão real do broker diferente de 15%; dados de conversão das primeiras reservas; ou necessidade de preço/desconto por canal ou temporada (tabela de preços).
- Responsável: produto
- Substitui: DEC-0001
- Substituída por: Nenhuma
- Motivo da mudança: A coleta real com hotel comparável (VAL-0002) mostrou que 5% deixava o preço 10,75% abaixo do mercado — fora da faixa de tolerância por ser conservador demais. 11,76% é o valor que mantém ~5% de vantagem com comissão de 15%, preservando competitividade e capturando margem.

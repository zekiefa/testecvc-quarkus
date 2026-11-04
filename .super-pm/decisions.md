## DEC-0001：Qual a taxa de serviço comercial aplicada sobre a tarifa da reserva?
<!-- dcid: DEC-0001; key: f2dcf02afd7abb9b1e6fa5fb233b1459202f2f9d45bb592a9308de2b0fef90b2; status: current; created: 2026-11-03 -->
- 状态：当前
- 日期：2026-11-03
- 决定：Aplicar 5% (500 bps) sobre a tarifa base do broker, incidindo sobre adulto e criança, como markup embutido no preço final. Fixa para todas as reservas e configurável por ambiente em booking.fee-bps.
- 负向边界：Nunca aplicar taxa sobre valor já taxado (bitributação); nunca taxa negativa; nunca taxa zero em produção por omissão.
- 证据：Decisão de posicionamento: 5% mantém o preço final próximo da tarifa do broker e ainda cobre o custo de intermediação. Ainda sem cotação de concorrentes na época da decisão (a tentativa posterior está em VAL-0001).
- 待验证：cotação agregada tentada em VAL-0001; critério original não aplicável
- 下一步验证：Revisar a taxa quando houver broker real com inventário comparável ou dados de conversão das primeiras reservas; a cotação de concorrentes (VAL-0001) não permitiu aplicar o critério de 10%.
- 重审触发：Margem insuficiente, renegociação com parceiro, ou necessidade de taxa por canal/temporada.
- 负责人：produto
- 替代了：无
- 被替代为：无
- 变更原因：无

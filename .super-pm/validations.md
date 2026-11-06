# Super PM 验证记录

## VAL-0001：Tentativa de validação de mercado por cotação agregada (Serra Negra)
<!-- valid: VAL-0001 -->
- 日期：2026-11-04
- 假设：5% mantém o preço final dentro de 10% do mercado de Serra Negra para 1 adulto + 1 criança.
- 预期结果：待确认
- 方法与样本：Média dos top-3 de Airbnb, Booking e Trivago para Serra Negra, 1 adulto + 1 criança, 10→13/04/2027, preço POR NOITE. Produtos não equivalentes entre si e sem correspondência no broker.
- 判断阈值：Diferença acima de 10% em relação ao concorrente reabre a decisão.
- 观察结果：R$ 712 / R$ 1.190 / R$ 1.904 por noite (medianas dos top-3 por site). Mediana R$ 1.190; spread de 167% entre o menor e o maior.
- 解释：A dispersão entre sites (R$ 1.192/noite) é cerca de 50x a diferença entre as taxas de 5% e 7% (R$ 23,80/noite na mediana). Os três valores vêm de produtos distintos (apartamento inteiro no Airbnb vs diária de hotel no Booking) e de médias de top-3, sem nenhum comparável presente no nosso broker (fixture só tem Gotham City). Conclusão: o critério de ±10% não é aplicável com estes dados — o instrumento tem erro maior que o efeito a medir.
- 决策变化：mantida
- 下一步：Trocar o critério de validação: revisar a taxa quando existir broker real com inventário comparável ou quando houver dados de conversão das primeiras reservas. Manter 5% até então.
- 关联决策：DEC-0001

## VAL-0002：Coleta real com hotel comparável — Hotel Fazenda Vale da Cachoeira
<!-- valid: VAL-0002 -->
- 日期：2026-11-06
- 假设：5% de markup mantém o preço final dentro de 10% do mercado para o mesmo hotel e datas.
- 预期结果：待确认
- 方法与样本：Hotel Fazenda Vale da Cachoeira (Serra Negra), 10-13/04/2027, 1 adulto + 1 criança, 3 noites. Coleta 05/11/2026: Booking 2.600,00 · Decolar 2.478,00 · Hoteis.com 2.476,00. Líquido não exposto pelos sites; inferido por comissão (15% / 15% / 15-20%) via líquido = bruto x (1 - comissão).
- 判断阈值：Desvio acima de 10% em relação ao mercado reabre a decisão (critério simétrico).
- 观察结果：Spread 5,01%. Nosso preço com 5%: 2.209,83 total (-10,75% vs 2.476). Com 20% de comissão: 2.079,84 (-16,00%). Taxas: 5,88% -> delta -10%; 11,76% -> delta -5%; 17,65% -> paridade.
- 解释：Instrumento agora mede: mesmo hotel, spread de 5,01% entre sites (antes 167%, comparando hotéis diferentes). Resultado fora da faixa de 10%, mas na direção favorável: 10,75% ABAIXO do mercado — a taxa de 5% era conservadora. O desvio depende só de comissão e taxa: delta = (1-c)(1+f)-1. Ação: taxa para 11,76% (DEC-0002), delta ~-5,00%.
- 决策变化：alterada: taxa 5% -> 11,76% (DEC-0002)
- 下一步：Manter 11,76% como posição de entrada do MVP; revisar com a comissão real do broker ou com dados de conversão. Preço por canal/temporada fica para tabela de preço/desconto, fora do MVP.
- 关联决策：DEC-0001

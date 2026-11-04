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

package br.com.cvc.evaluation.booking;

import java.math.BigDecimal;

/**
 * Uma noite do período, com o preço congelado no momento da reserva.
 *
 * <p>É um snapshot: mudança posterior de tarifa no broker não altera reserva já criada
 * (docs/arquitetura.md §3.3). {@code stayDate} fica em ISO-8601 para que o JSON no banco
 * seja legível e estável, sem depender de configuração de serialização de datas.</p>
 */
public record NightlyRate(String stayDate, BigDecimal adultAmount, BigDecimal childAmount) {
}

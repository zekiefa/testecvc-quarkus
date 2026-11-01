package br.com.cvc.evaluation.service.provider;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuração de negócio da reserva ({@code booking.*}).
 *
 * <p>Todo o prefixo {@code booking} precisa estar declarado aqui: o SmallRye valida que
 * nenhuma chave sob o prefixo fique sem destino (SRCFG00050).</p>
 */
@ConfigMapping(prefix = "booking")
public interface BookingSettings {
    /**
     * Taxa aplicada sobre a tarifa base, em <b>basis points</b> (700 = 7,00%).
     *
     * <p>A unidade é decisão fechada (ADR-004); o <b>valor</b> ainda está em aberto:
     * {@code 700} é apenas o valor provisório de teste local, marcado como TODO(PRODUTO).</p>
     */
    int feeBps();

    /** Moeda do snapshot de preço, ISO-4217. */
    String currency();

    /**
     * Quantos quartos deste tipo existem por noite quando o inventário é materializado.
     * Premissa nossa, não dado do broker (ADR-002): 1 por quarto/noite.
     */
    @WithDefault("1")
    int defaultCapacity();
}

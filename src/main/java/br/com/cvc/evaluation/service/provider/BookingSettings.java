package br.com.cvc.evaluation.service.provider;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuração de negócio da reserva ({@code booking.*}).
 *
 * <p>Qualquer prefixo {@code booking} precisa estar declarado aqui: o SmallRye valida que
 * nenhuma chave sob o prefixo fique sem destino (SRCFG00050).</p>
 */
@ConfigMapping(prefix = "booking")
public interface BookingSettings {
    /**
     * Taxa aplicada sobre a tarifa base, em <b>basis points</b> (500 = 5,00%;
     * valor decidido pelo produto e registrado no ADR-004).
     *
     * <p>A unidade é decisão fechada (ADR-004) e o <b>valor</b> também: 500 bps de
     * markup ao hóspede, sobre adulto e criança, fixo para todas as reservas.</p>
     */
    int feeBps();

    /**
     * Período máximo de uma estadia, em noites.
     *
     * <p>Limite único da aplicação: reserva, criação e inventário leem daqui. Vem de
     * {@code booking.max-nights} (ambiente: {@code BOOKING_MAX_NIGHTS}), padrão 30.</p>
     */
    @WithDefault("30")
    int maxNights();

    /** Moeda do snapshot de preço, ISO-4217. */
    String currency();

    /**
     * Quantos quartos deste tipo existem por noite quando o inventário é materializado.
     * Premissa nossa, não dado do broker (ADR-002): 1 por quarto/noite.
     */
    @WithDefault("1")
    int defaultCapacity();
}

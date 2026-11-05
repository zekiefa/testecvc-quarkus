package br.com.cvc.evaluation.catalog;

import java.time.LocalDate;
import java.util.List;

import br.com.cvc.evaluation.broker.BrokerQuoteClient;
import br.com.cvc.evaluation.broker.dto.BrokerHotelDetail;
import br.com.cvc.evaluation.broker.dto.BrokerQuoteRequest;
import br.com.cvc.evaluation.broker.dto.BrokerSearchResponse;
import br.com.cvc.evaluation.exceptions.BrokerUnavailableException;
import br.com.cvc.evaluation.exceptions.HotelNotFoundException;
import br.com.cvc.evaluation.exceptions.InvalidRequestException;
import br.com.cvc.evaluation.exceptions.RateUnavailableException;
import br.com.cvc.evaluation.service.provider.BookingSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Porta de acesso ao catálogo do broker (ADR-002): traduz falhas de transporte em
 * respostas do nosso domínio, para que nenhum outro serviço precise conhecer
 * {@code WebApplicationException}.
 */
@ApplicationScoped
public class BrokerCatalog {

    private final BrokerQuoteClient quoteClient;
    private final BookingSettings settings;

    public BrokerCatalog(@RestClient final BrokerQuoteClient quoteClient, final BookingSettings settings) {
        this.quoteClient = quoteClient;
        this.settings = settings;
    }

    /**
     * Busca por destino no contrato v1.
     *
     * <p>Erro de entrada do nosso lado (400) não é indisponibilidade: é bug nosso, e vira
     * {@code InvalidRequestException}. O que é do broker (5xx, transporte, credencial) vira
     * {@code 503}.</p>
     */
    public List<QuotedHotel> search(final String cityCode, final LocalDate checkIn, final LocalDate checkOut,
                    final int adults, final int children) {
        try {
            final var response = this.quoteClient.search(cityCode, checkIn, checkOut, adults, children);
            if (response == null || response.items() == null) {
                return List.of();
            }

            return response.items().stream().map(BrokerCatalog::toQuotedHotel).toList();
        } catch (final WebApplicationException e) {
            if (e.getResponse() == null) {
                throw new BrokerUnavailableException("Broker failed: " + e.getMessage());
            }
            if (e.getResponse().getStatus() == 400) {
                throw new InvalidRequestException("Broker rejected the search parameters");
            }
            throw new BrokerUnavailableException("Broker failed: " + e.getMessage());
        } catch (final ProcessingException e) {
            throw new BrokerUnavailableException("Broker unreachable: " + e.getMessage());
        }
    }

    /** Catálogo do hotel no contrato v1. 404 do broker é "hotel não existe", não indisponibilidade. */
    public BrokerHotelDetail hotelDetail(final Integer hotelId) {
        try {
            final var hotel = this.quoteClient.hotel(hotelId.longValue());
            if (hotel == null) {
                throw new HotelNotFoundException("Hotel not found: " + hotelId);
            }
            return hotel;
        } catch (final WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                throw new HotelNotFoundException("Hotel not found: " + hotelId);
            }
            throw new BrokerUnavailableException("Broker failed: " + e.getMessage());
        } catch (final ProcessingException e) {
            throw new BrokerUnavailableException("Broker unreachable: " + e.getMessage());
        }
    }

    private static QuotedHotel toQuotedHotel(final BrokerSearchResponse.Hit hit) {
        final List<QuotedHotel.Night> nights = hit.nights() == null
                        ? List.of()
                        : hit.nights().stream()
                                        .map(night -> new QuotedHotel.Night(night.date(), night.adult(),
                                                        night.child()))
                                        .toList();

        return new QuotedHotel(hit.hotelId().intValue(), hit.hotelName(), hit.cityName(),
                        hit.roomTypeId().intValue(), hit.roomTypeName(), hit.ratePlanId().intValue(),
                        hit.ratePlanName(), hit.board(), hit.cancellationPolicy(), hit.currency(), nights);
    }

    /**
     * Cotação do período no contrato v1 do broker.
     *
     * <p>Duas violações de contrato são tratadas como indisponibilidade, não como dado válido:
     * cotação que não cobre todas as noites e moeda diferente da nossa. Aceitar qualquer uma
     * das duas seria gravar preço errado.</p>
     */
    public List<QuotedNight> quote(final Integer hotelId, final Integer roomId, final Integer ratePlanId,
                    final LocalDate checkIn, final LocalDate checkOut, final Integer adults, final Integer children,
                    final long nights) {
        try {
            final var quote = this.quoteClient.quote(new BrokerQuoteRequest(hotelId.longValue(), roomId.longValue(),
                            ratePlanId.longValue(), checkIn, checkOut, adults, children));

            if (quote == null || quote.nights() == null || quote.nights().size() != nights) {
                throw new BrokerUnavailableException("Broker returned a quote that does not cover the period");
            }
            if (!this.settings.currency().equals(quote.currency())) {
                throw new BrokerUnavailableException("Broker quoted in " + quote.currency() + ", expected "
                                + this.settings.currency());
            }

            return quote.nights().stream()
                            .map(night -> new QuotedNight(night.date(), night.adult(), night.child()))
                            .toList();
        } catch (final WebApplicationException e) {
            if (e.getResponse() == null) {
                throw new BrokerUnavailableException("Broker failed: " + e.getMessage());
            }
            throw switch (e.getResponse().getStatus()) {
                case 404 -> new HotelNotFoundException("Hotel, room or rate plan not found");
                case 409 -> new RateUnavailableException("Broker has no rate for some night in the period");
                default -> new BrokerUnavailableException("Broker failed: " + e.getMessage());
            };
        } catch (final ProcessingException e) {
            throw new BrokerUnavailableException("Broker unreachable: " + e.getMessage());
        }
    }


}

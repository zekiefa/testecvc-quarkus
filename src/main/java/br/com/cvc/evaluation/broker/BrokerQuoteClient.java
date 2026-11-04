package br.com.cvc.evaluation.broker;

import br.com.cvc.evaluation.broker.dto.BrokerQuote;
import br.com.cvc.evaluation.broker.dto.BrokerQuoteRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Cliente do contrato v1 do broker (repositório separado: testecvc-broker-quarkus).
 *
 * <p>Convive com {@link BrokerService} (contrato legado, usado pela busca e pelo detalhe)
 * até a migração do MB3.2. A reserva já usa o contrato novo.</p>
 */
@Path("/")
@RegisterRestClient(configKey = "broker-v1")
public interface BrokerQuoteClient {

    @POST
    @Path("v1/quotes")
    BrokerQuote quote(BrokerQuoteRequest request);
}

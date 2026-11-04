# Broker de Hotéis — documento movido

A arquitetura do broker passou a viver no repositório próprio:
**[testecvc-broker-quarkus](https://github.com/zekiefa/testecvc-broker-quarkus)**

| O quê | Onde |
|---|---|
| Arquitetura (v0.1) | [`docs/arquitetura.md`](https://github.com/zekiefa/testecvc-broker-quarkus/blob/main/docs/arquitetura.md) |
| Contrato OpenAPI v1 | [`openapi/broker-v1.yaml`](https://github.com/zekiefa/testecvc-broker-quarkus/blob/main/openapi/broker-v1.yaml) |

**Por que saiu daqui:** o broker é um sistema com histórico, contrato e roadmap próprios.
Manter uma cópia neste repositório criaria duas verdades que divergem na primeira alteração.

## O que continua neste repositório

- a **decisão** de construir o broker (épico `tcq-ptp`);
- o **consumidor**: `BrokerCatalog` / `BrokerService`, que hoje aponta para a fixture e deverá
  apontar para o broker real — marco **MB3** do outro repositório;
- a restrição **C2** do [documento de arquitetura](../arquitetura.md), que registra a mudança de contexto.

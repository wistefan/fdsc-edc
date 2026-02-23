package org.seamware.edc.store;

import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.jetbrains.annotations.NotNull;
import org.seamware.edc.tmf.ProductCatalogApiClient;

import java.util.stream.Stream;

public class TMFBackedContractDefinitionStore implements ContractDefinitionStore {

    private final Monitor monitor;
    private final ProductCatalogApiClient productCatalogApi;
    private final TMFEdcMapper tmfEdcMapper;

    public TMFBackedContractDefinitionStore(Monitor monitor, ProductCatalogApiClient productCatalogApi, TMFEdcMapper tmfEdcMapper) {
        this.monitor = monitor;
        this.productCatalogApi = productCatalogApi;
        this.tmfEdcMapper = tmfEdcMapper;
    }

    @Override
    public @NotNull Stream<ContractDefinition> findAll(QuerySpec querySpec) {
        throw new UnsupportedOperationException("Querying for contract definitions is currently not supported.");
    }

    @Override
    public ContractDefinition findById(String s) {
        try {
            return productCatalogApi.getProductOfferingByExternalId(s)
                    .flatMap(tmfEdcMapper::fromProductOffer)
                    .orElse(null);
        } catch (RuntimeException e) {
            monitor.warning("Was not able to successfully resolve the product offer.", e);
            return null;
        }
    }

    @Override
    public StoreResult<Void> save(ContractDefinition contractDefinition) {
        throw new UnsupportedOperationException("Storing contract definitions is currently not supported.");
    }

    @Override
    public StoreResult<Void> update(ContractDefinition contractDefinition) {
        throw new UnsupportedOperationException("Updating contract definitions is currently not supported.");
    }

    @Override
    public StoreResult<ContractDefinition> deleteById(String s) {
        throw new UnsupportedOperationException("Deleting contract definitions is currently not supported.");
    }
}

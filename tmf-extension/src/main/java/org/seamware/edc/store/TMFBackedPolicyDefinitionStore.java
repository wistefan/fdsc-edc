package org.seamware.edc.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.seamware.edc.tmf.ProductCatalogApiClient;

import java.util.logging.Logger;
import java.util.stream.Stream;

public class TMFBackedPolicyDefinitionStore implements PolicyDefinitionStore {

    private static final Logger LOGGER = Logger.getLogger("SeamPolicyDefinitionStore");

    private final Monitor monitor;
    private final ObjectMapper objectMapper;

    private final ProductCatalogApiClient productCatalogApiClient;

    public TMFBackedPolicyDefinitionStore(Monitor monitor, ObjectMapper objectMapper, ProductCatalogApiClient productCatalogApiClient) {
        this.monitor = monitor;
        this.objectMapper = objectMapper;
        this.productCatalogApiClient = productCatalogApiClient;
    }

    @Override
    public PolicyDefinition findById(String policyDefinitionId) {
        Policy policy = productCatalogApiClient.getByPolicyId(policyDefinitionId);

        return PolicyDefinition.Builder.newInstance()
                .policy(policy)
                .id(TMFEdcMapper.getIdFromPolicy(policy))
                .build();
    }

    @Override
    public Stream<PolicyDefinition> findAll(QuerySpec querySpec) {
        throw new UnsupportedOperationException("Querying for policy definitions currently is unsupported.");
    }

    @Override
    public StoreResult<PolicyDefinition> create(PolicyDefinition policyDefinition) {
        throw new UnsupportedOperationException("Creating policy definitions currently is unsupported.");
    }

    @Override
    public StoreResult<PolicyDefinition> update(PolicyDefinition policyDefinition) {
        throw new UnsupportedOperationException("Updating policy definitions currently is unsupported.");
    }

    @Override
    public StoreResult<PolicyDefinition> delete(String s) {
        throw new UnsupportedOperationException("Deleting policy definitions currently is unsupported.");
    }
}

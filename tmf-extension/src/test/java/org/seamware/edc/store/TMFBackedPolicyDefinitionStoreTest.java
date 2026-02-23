package org.seamware.edc.store;

import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.seamware.edc.tmf.ProductCatalogApiClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TMFBackedPolicyDefinitionStoreTest {


    private static final String TEST_ID = "test-id";

    private ProductCatalogApiClient productCatalogApiClient;
    private TMFBackedPolicyDefinitionStore tmfBackedPolicyDefinitionStore;


    @BeforeEach
    public void setup() {
        productCatalogApiClient = mock(ProductCatalogApiClient.class);

        tmfBackedPolicyDefinitionStore = new TMFBackedPolicyDefinitionStore(mock(Monitor.class), productCatalogApiClient);
    }


    @Test
    public void testFindById_success() {

        Policy testPolicy = getTestPolicy();
        PolicyDefinition expectedDefinition = PolicyDefinition.Builder.newInstance()
                .policy(testPolicy)
                .id(TEST_ID)
                .build();

        when(productCatalogApiClient.getByPolicyId(eq(TEST_ID))).thenReturn(Optional.of(testPolicy));

        assertEquals(expectedDefinition, tmfBackedPolicyDefinitionStore.findById(TEST_ID), "The asset should successfully be returned.");
    }

    @Test
    public void testFindById_no_spec() {
        when(productCatalogApiClient.getByPolicyId(eq(TEST_ID))).thenReturn(Optional.empty());

        assertNull(tmfBackedPolicyDefinitionStore.findById(TEST_ID), "If no corresponding spec exists, null should be returned.");
    }

    @Test
    public void testFindById_api_error() {
        when(productCatalogApiClient.getByPolicyId(eq(TEST_ID))).thenThrow(new RuntimeException("Something bad happened"));

        assertThrows(EdcPersistenceException.class, () -> tmfBackedPolicyDefinitionStore.findById(TEST_ID), "If an error happens, an EdcPersistence Exception should be thrown.");
    }

    private Policy getTestPolicy() {
        return Policy.Builder
                .newInstance()
                .extensibleProperty("http://www.w3.org/ns/odrl/2/uid", TEST_ID)
                .build();
    }
}
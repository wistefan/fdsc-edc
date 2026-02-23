package org.seamware.edc.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QuerySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.seamware.edc.domain.AgreementState;
import org.seamware.edc.domain.ContractNegotiationState;
import org.seamware.edc.domain.ExtendableAgreementVO;
import org.seamware.edc.domain.ExtendableQuoteVO;
import org.seamware.edc.tmf.*;
import org.seamware.tmforum.agreement.model.AgreementVO;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TMFBackedContractNegotiationStoreTest {

    private static final String TEST_AGREEMENT_ID = "test-agreement";
    private static final String TEST_PROVIDER_ID = "provider-id";
    private static final String TEST_CONSUMER_ID = "consumer-id";
    private static final String TEST_ASSET_ID = "asset-id";
    private static final String TEST_POLICY_ID = "policy-id";
    private static final String TEST_COUNTER_PARTY_ID = "counter-party-id";
    private static final String TEST_COUNTER_PARTY_ADDRESS = "http://counter.party";
    private static final String TEST_PROTOCOL = "v1";
    private static final String TEST_PARTICIPANT_ID = "test-participant";
    private static final String TEST_CONTROL_PLANE_ID = "test-control-plane";

    private QuoteApiClient quoteApiClient;
    private AgreementApiClient agreementApiClient;
    private ProductOrderApiClient productOrderApiClient;
    private ProductCatalogApiClient productCatalogApiClient;
    private ProductInventoryApiClient productInventoryApiClient;
    private ParticipantResolver participantResolver;
    private TMFEdcMapper tmfEdcMapper;
    private CriterionOperatorRegistry criterionOperatorRegistry;
    private Clock clock = Clock.fixed(Instant.MIN, ZoneId.systemDefault());

    private TMFBackedContractNegotiationStore tmfBackedContractNegotiationStore;

    @BeforeEach
    public void setup() {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.addMixIn(Policy.Builder.class, UnknownPropertyMixin.class);
        objectMapper.registerModule(new JavaTimeModule());

        quoteApiClient = mock(QuoteApiClient.class);
        agreementApiClient = mock(AgreementApiClient.class);
        productOrderApiClient = mock(ProductOrderApiClient.class);
        productCatalogApiClient = mock(ProductCatalogApiClient.class);
        productInventoryApiClient = mock(ProductInventoryApiClient.class);
        participantResolver = mock(ParticipantResolver.class);
        tmfEdcMapper = mock(TMFEdcMapper.class);
        criterionOperatorRegistry = mock(CriterionOperatorRegistry.class);

        tmfBackedContractNegotiationStore = new TMFBackedContractNegotiationStore(
                mock(Monitor.class), objectMapper,
                quoteApiClient, agreementApiClient,
                productOrderApiClient, productCatalogApiClient,
                productInventoryApiClient, participantResolver,
                tmfEdcMapper, TEST_PARTICIPANT_ID,
                clock, TEST_CONTROL_PLANE_ID,
                criterionOperatorRegistry);
    }

    @Test
    public void testFindContractAgreement_success() {
        ExtendableAgreementVO extendableAgreementVO = new ExtendableAgreementVO()
                .setExternalId(TEST_AGREEMENT_ID);
        extendableAgreementVO.setStatus(AgreementState.AGREED.getValue());
        when(agreementApiClient.findByContractId(eq(TEST_AGREEMENT_ID))).thenReturn(Optional.of(extendableAgreementVO));
        when(tmfEdcMapper.toContractAgreement(eq(extendableAgreementVO))).thenReturn(getTestAgreement());

        assertEquals(getTestAgreement(), tmfBackedContractNegotiationStore.findContractAgreement(TEST_AGREEMENT_ID), "The correct agreement should be returned.");
    }

    @Test
    public void testFindContractAgreement_not_agreed() {
        ExtendableAgreementVO extendableAgreementVO = new ExtendableAgreementVO()
                .setExternalId(TEST_AGREEMENT_ID);
        extendableAgreementVO.setStatus(AgreementState.IN_PROCESS.getValue());
        when(agreementApiClient.findByContractId(eq(TEST_AGREEMENT_ID))).thenReturn(Optional.of(extendableAgreementVO));

        assertNull(tmfBackedContractNegotiationStore.findContractAgreement(TEST_AGREEMENT_ID), "If the agreement is not agreed, no contract agreement should be returned.");
    }

    @Test
    public void testFindContractAgreement_no_agreement() {
        when(agreementApiClient.findByContractId(eq(TEST_AGREEMENT_ID))).thenReturn(Optional.empty());

        assertNull(tmfBackedContractNegotiationStore.findContractAgreement(TEST_AGREEMENT_ID), "If no agreement is returned, no contract agreement should be returned.");
    }

    @Test
    public void testFindContractAgreement_unmappable_agreement() {
        ExtendableAgreementVO extendableAgreementVO = new ExtendableAgreementVO()
                .setExternalId(TEST_AGREEMENT_ID);
        extendableAgreementVO.setStatus(AgreementState.AGREED.getValue());
        when(agreementApiClient.findByContractId(eq(TEST_AGREEMENT_ID))).thenReturn(Optional.of(extendableAgreementVO));
        when(tmfEdcMapper.toContractAgreement(eq(extendableAgreementVO))).thenThrow(new RuntimeException("Was not able to map the agreement."));

        assertNull(tmfBackedContractNegotiationStore.findContractAgreement(TEST_AGREEMENT_ID), "If the agreement is not mappable, no contract agreement should be returned.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getValidNegotiations")
    public void testQueryNegotiations_success(String name, QuerySpec querySpec, List<ExtendableQuoteVO> negotiationQuotes, int numberOfNegotiations) {

        when(quoteApiClient.getQuotes(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int offset = invocation.getArgument(0);
                    int limit = invocation.getArgument(1);

                    int fromIndex = Math.min(offset, negotiationQuotes.size());
                    int toIndex = Math.min(offset + limit, negotiationQuotes.size());

                    return negotiationQuotes.subList(fromIndex, toIndex);
                });
        when(tmfEdcMapper.toContractNegotiation(any(), any(), any(), any()))
                .thenReturn(getValidNegotiation());
        assertEquals(numberOfNegotiations, tmfBackedContractNegotiationStore.queryNegotiations(querySpec).toList().size());
    }

    private static ContractNegotiation getValidNegotiation() {
        return ContractNegotiation.Builder
                .newInstance()
                .counterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
                .counterPartyId(TEST_COUNTER_PARTY_ID)
                .protocol(TEST_PROTOCOL)
                .build();
    }

    @Test
    public void testQueryNegotiations_no_quotes() {

        when(quoteApiClient.getQuotes(anyInt(), anyInt())).thenReturn(List.of());
        assertEquals(0,
                tmfBackedContractNegotiationStore.queryNegotiations(QuerySpec.Builder.newInstance().offset(0).limit(100).build()).count(),
                "An empty stream should be returned if no quotes are returned.");
    }

    @Test
    public void testQueryNegotiations_skip_failing_quotes() {

        List<ExtendableQuoteVO> workingQuotes = getQuotes("negotiation-1", TEST_CONTROL_PLANE_ID, 3);
        List<ExtendableQuoteVO> failingQuotes = getQuotes("negotiation-2", TEST_CONTROL_PLANE_ID, 3);

        when(quoteApiClient.getQuotes(anyInt(), anyInt())).thenReturn(Stream.concat(workingQuotes.stream(), failingQuotes.stream()).toList());
        when(tmfEdcMapper.toContractNegotiation(eq(workingQuotes), any(), any(), any())).thenReturn(getValidNegotiation());
        when(tmfEdcMapper.toContractNegotiation(eq(failingQuotes), any(), any(), any())).thenThrow(new RuntimeException("Unmappable"));
        assertEquals(1,
                tmfBackedContractNegotiationStore.queryNegotiations(QuerySpec.Builder.newInstance().offset(0).limit(100).build()).count(),
                "Only the successfully mapped negotiations should be returned.");
    }

    @ParameterizedTest
    @MethodSource("getValidAgreements")
    public void testQueryAgreements_success(String name, List<AgreementHolder> agreementHolders, QuerySpec querySpec, int expectedAgs) {
        List<ExtendableAgreementVO> agreementVOS = agreementHolders.stream()
                .map(AgreementHolder::agreementVO)
                .toList();
        when(agreementApiClient.getAgreements(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int offset = invocation.getArgument(0);
                    int limit = invocation.getArgument(1);

                    int fromIndex = Math.min(offset, agreementVOS.size());
                    int toIndex = Math.min(offset + limit, agreementVOS.size());

                    return agreementVOS.subList(fromIndex, toIndex);
                });

        agreementHolders.forEach(ah -> when(tmfEdcMapper.toContractAgreement(eq(ah.agreementVO()))).thenReturn(ah.contractAgreement()));
        assertEquals(expectedAgs, tmfBackedContractNegotiationStore.queryAgreements(querySpec).count(), name);
    }

    @Test
    public void testQueryAgreements_empty_stream_if_no_agreement() {
        when(agreementApiClient.getAgreements(anyInt(), anyInt())).thenReturn(List.of());

        assertEquals(0, tmfBackedContractNegotiationStore.queryAgreements(QuerySpec.max()).count(), "If no agreements exist, an empty stream should be returned.");
    }

    @Test
    public void testQueryAgreements_ignore_failing_agreements() {
        when(agreementApiClient.getAgreements(anyInt(), anyInt())).thenReturn(getAgreements(10, "agg", AgreementState.AGREED.getValue()).stream().map(AgreementHolder::agreementVO).toList());
        when(tmfEdcMapper.toContractAgreement(any()))
                .thenThrow(new RuntimeException("Unmappable"))
                .thenReturn(getTestAgreement());
        assertEquals(9, tmfBackedContractNegotiationStore.queryAgreements(QuerySpec.max()).count(), "If an agreement cannot be mapped, it should be ignored.");
    }

    private static Stream<Arguments> getValidAgreements() {
        return Stream.of(
                Arguments.of("All agreements should have been returned.",
                        getAgreements(10, "agg", AgreementState.AGREED.getValue()),
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(), 10),
                Arguments.of("Agreements should have been returned, respecting limits.",
                        getAgreements(10, "agg", AgreementState.AGREED.getValue()),
                        QuerySpec.Builder.newInstance().offset(0).limit(5).build(), 5),
                Arguments.of("Agreements should have been returned, respecting the offset.",
                        getAgreements(10, "agg", AgreementState.AGREED.getValue()),
                        QuerySpec.Builder.newInstance().offset(5).limit(10).build(), 5),
                Arguments.of("Only agreed agreements should have been returned, respecting the offset.",
                        Stream.concat(getAgreements(10, "agg", AgreementState.AGREED.getValue()).stream(), getAgreements(5, "in-process", AgreementState.IN_PROCESS.getValue()).stream()).toList(),
                        QuerySpec.Builder.newInstance().offset(0).limit(10).build(), 10)
        );
    }

    private static List<AgreementHolder> getAgreements(int numAgs, String idPrefix, String agreementState) {
        List<AgreementHolder> agreementHolders = new ArrayList<>();
        for (int i = 0; i < numAgs; i++) {
            ContractAgreement contractAgreement = ContractAgreement.Builder.newInstance()
                    .id(String.format("%s-%s", idPrefix, i))
                    .policy(getTestPolicy())
                    .assetId(TEST_ASSET_ID)
                    .consumerId(TEST_CONSUMER_ID)
                    .providerId(TEST_PROVIDER_ID)
                    .build();
            ExtendableAgreementVO extendableAgreementVO = new ExtendableAgreementVO()
                    .setExternalId(String.format("%s-%s", idPrefix, i));
            extendableAgreementVO.setStatus(agreementState);

            agreementHolders.add(new AgreementHolder(extendableAgreementVO, contractAgreement));
        }
        return agreementHolders;
    }

    private static Stream<Arguments> getValidNegotiations() {
        return Stream.of(
                Arguments.of("Get one negotiation from the quotes.",
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(),
                        getQuotes("negotiation-1", TEST_CONTROL_PLANE_ID, 3),
                        1),
                Arguments.of("Get negotiations from the quotes with limit.",
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(),
                        getNegotitations(150),
                        100),
                Arguments.of("Get negotiations from the quotes with limit.",
                        QuerySpec.Builder.newInstance().offset(0).limit(50).build(),
                        getNegotitations(150),
                        50),
                Arguments.of("Get negotiations from the quotes with offset and limit.",
                        QuerySpec.Builder.newInstance().offset(100).limit(100).build(),
                        getNegotitations(150),
                        50),
                Arguments.of("Get the negotiations from the quotes.",
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(),
                        Stream.concat(getQuotes("negotiation-1", TEST_CONTROL_PLANE_ID, 3).stream(), getQuotes("negotiation-2", TEST_CONTROL_PLANE_ID, 1).stream()).toList(),
                        2),
                Arguments.of("Get only the negotiations for this controlplane.",
                        QuerySpec.Builder.newInstance().offset(0).limit(100).build(),
                        Stream.concat(
                                Stream.concat(
                                        getQuotes("negotiation-1", TEST_CONTROL_PLANE_ID, 3).stream(),
                                        getQuotes("negotiation-2", TEST_CONTROL_PLANE_ID, 1).stream()),
                                getQuotes("negotiation-2", "the-other-plane", 1).stream()).toList(),
                        2)
        );
    }

    private static List<ExtendableQuoteVO> getNegotitations(int numNegs) {
        List<ExtendableQuoteVO> quoteVOS = new ArrayList<>();
        for (int i = 0; i < numNegs; i++) {
            quoteVOS.addAll(getQuotes(String.format("neg-%s", i), TEST_CONTROL_PLANE_ID, 3));
        }
        return quoteVOS;
    }

    private static List<ExtendableQuoteVO> getQuotes(String negotiationId, String controlPlane, int numQuotes) {
        List<ExtendableQuoteVO> extendableQuoteVOS = new ArrayList<>();
        for (int i = 0; i < numQuotes; i++) {
            ExtendableQuoteVO extendableQuoteVO = new ExtendableQuoteVO();
            extendableQuoteVO.setExternalId(negotiationId);
            extendableQuoteVO.setContractNegotiationState(new ContractNegotiationState().setControlplane(controlPlane));
            extendableQuoteVOS.add(extendableQuoteVO);
        }

        return extendableQuoteVOS;
    }

    private record NegotiationHolder(List<ExtendableQuoteVO> extendableQuoteVOS, ContractNegotiation contractNegotiation) {
    }

    private record AgreementHolder(ExtendableAgreementVO agreementVO, ContractAgreement contractAgreement) {
    }

    private static ContractAgreement getTestAgreement() {
        return ContractAgreement.Builder.newInstance()
                .id(TEST_AGREEMENT_ID)
                .providerId(TEST_PROVIDER_ID)
                .consumerId(TEST_CONSUMER_ID)
                .assetId(TEST_ASSET_ID)
                .policy(getTestPolicy())
                .build();
    }


    private static Policy getTestPolicy() {
        return Policy.Builder
                .newInstance()
                .extensibleProperty("http://www.w3.org/ns/odrl/2/uid", TEST_POLICY_ID)
                .build();
    }

}
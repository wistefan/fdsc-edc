package org.seamware.edc.store;

import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.seamware.edc.domain.*;
import org.seamware.tmforum.quote.model.QuoteCreateVO;
import org.seamware.tmforum.quote.model.QuoteItemVO;
import org.seamware.tmforum.quote.model.QuoteStateTypeVO;
import org.seamware.tmforum.quote.model.RelatedPartyVO;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * In order to keep the tests readable, all tests for the "save"-method are in this class.
 */
public class TMFBackedContractNegotiationStorePersistenceTest extends TMFBackedContractNegotiationStoreTest {
    public static final String NEW_QUOTE_ID = "new-quote";


    // -- INITIAL ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("getInitialStates_update")
    public void testInitialSave_success_update(String name, ContractNegotiation contractNegotiation, List<ExtendableQuoteVO> negotiationQuotes, List<ExtendableQuoteItemVO> quoteItems, String expectedQuoteId, ExtendableQuoteUpdateVO expectedUpdate) {

        Map<String, List<ExtendableQuoteVO>> quotesByNeg = new HashMap<>();
        negotiationQuotes.forEach(nq -> {
            if (quotesByNeg.containsKey(nq.getExternalId())) {
                quotesByNeg.get(nq.getExternalId()).add(nq);
            } else {
                quotesByNeg.put(nq.getExternalId(), new ArrayList<>(List.of(nq)));
            }
        });
        quotesByNeg.forEach((key, value) -> when(quoteApiClient.findByNegotiationId(eq(key))).thenReturn(value));

        ArgumentCaptor<ExtendableQuoteUpdateVO> quoteCaptor = ArgumentCaptor.forClass(ExtendableQuoteUpdateVO.class);
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);

        ExtendableProductOffering extendableProductOffering = getExtendableProductOffering();

        when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
                .thenReturn(Optional.of(extendableProductOffering));
        switch (contractNegotiation.getType()) {
            case PROVIDER -> quoteItems.forEach(qi -> when(tmfEdcMapper.fromProviderContractOffer(any(), any(), any())).thenReturn(qi));
            case CONSUMER -> quoteItems.forEach(qi -> when(tmfEdcMapper.fromConsumerContractOffer(any(), any())).thenReturn(qi));
        }

        when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class))).thenAnswer(invocation -> {
            ExtendableQuoteVO extendableQuoteVO = invocation.getArgument(0);
            return new TMFObjectMapperImpl().map(extendableQuoteVO);
        });

        tmfBackedContractNegotiationStore.save(contractNegotiation);

        verify(quoteApiClient).updateQuote(idCaptor.capture(), quoteCaptor.capture());

        verify(leaseHolder, times(1)).acquireLease(eq(contractNegotiation.getId()), any());
        verify(leaseHolder, times(1)).freeLease(eq(contractNegotiation.getId()), any());

        ExtendableQuoteUpdateVO extendableQuoteUpdateVO = quoteCaptor.getValue();
        assertEquals(expectedQuoteId, idCaptor.getValue(), "The correct quote should have been updated.");
        assertEquals(expectedUpdate.getExternalId(), extendableQuoteUpdateVO.getExternalId(), "The correct negotiationId should be included.");
        assertEquals(expectedUpdate.getContractNegotiationState(), extendableQuoteUpdateVO.getContractNegotiationState(), "The correct negotiation state should be stored.");
        assertEquals(expectedUpdate.getState(), extendableQuoteUpdateVO.getState(), "The quote state should be stored.");
        assertEquals(expectedUpdate.getQuoteItem(), extendableQuoteUpdateVO.getExtendableQuoteItem(), "The offerid should be set for the quote item.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getInitialStates_create")
    public void testInitialSave_success_create(String name, ContractNegotiation contractNegotiation, List<ExtendableQuoteVO> negotiationQuotes, List<ExtendableQuoteItemVO> quoteItems, ExtendableQuoteCreateVO expectedCreate) {
        Map<String, List<ExtendableQuoteVO>> quotesByNeg = new HashMap<>();
        negotiationQuotes.forEach(nq -> {
            if (quotesByNeg.containsKey(nq.getExternalId())) {
                quotesByNeg.get(nq.getExternalId()).add(nq);
            } else {
                quotesByNeg.put(nq.getExternalId(), new ArrayList<>(List.of(nq)));
            }
        });
        quotesByNeg.forEach((key, value) -> when(quoteApiClient.findByNegotiationId(eq(key))).thenReturn(value));

        ArgumentCaptor<ExtendableQuoteCreateVO> quoteCaptor = ArgumentCaptor.forClass(ExtendableQuoteCreateVO.class);

        ExtendableProductOffering extendableProductOffering = getExtendableProductOffering();

        when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class))).thenAnswer(invocation -> {
            ExtendableQuoteVO extendableQuoteVO = invocation.getArgument(0);
            return new TMFObjectMapperImpl().map(extendableQuoteVO);
        });
        when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
                .thenReturn(Optional.of(extendableProductOffering));

        switch (contractNegotiation.getType()) {
            case PROVIDER -> quoteItems.forEach(qi -> when(tmfEdcMapper.fromProviderContractOffer(any(), any(), any())).thenReturn(qi));
            case CONSUMER -> quoteItems.forEach(qi -> when(tmfEdcMapper.fromConsumerContractOffer(any(), any())).thenReturn(qi));
        }

        tmfBackedContractNegotiationStore.save(contractNegotiation);

        if (!negotiationQuotes.isEmpty()) {
            // in this case, we need to cancel
            verify(quoteApiClient, times(1)).updateQuote(any(), any());
        }

        verify(quoteApiClient).createQuote(quoteCaptor.capture());

        verify(leaseHolder, times(1)).acquireLease(eq(contractNegotiation.getId()), any());
        verify(leaseHolder, times(1)).freeLease(eq(contractNegotiation.getId()), any());

        ExtendableQuoteCreateVO extendableQuoteCreateVO = quoteCaptor.getValue();
        assertEquals(expectedCreate.getExternalId(), extendableQuoteCreateVO.getExternalId(), "The correct negotiationId should be included.");
        assertEquals(expectedCreate.getContractNegotiationState(), extendableQuoteCreateVO.getContractNegotiationState(), "The correct negotiation state should be stored.");
        assertEquals(expectedCreate.getQuoteItem(), extendableQuoteCreateVO.getExtendableQuoteItem(), "The offerid should be set for the quote item.");
        assertEquals(Set.of("Provider", "Consumer"), extendableQuoteCreateVO.getRelatedParty().stream().map(RelatedPartyVO::getRole).collect(Collectors.toSet()), "A consumer and a provider need to be included.");
    }

    private static Stream<Arguments> getInitialStates_update() {
        return Stream.of(
                Arguments.of("The initial state should properly be stored.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.INITIAL),
                        setActiveQuote(ContractNegotiationStates.INITIAL, "active-quote", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-quote",
                        expectedQuoteUpdate("INITIAL", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.INITIAL),
                        setActiveQuote(ContractNegotiationStates.INITIAL, "active-quote", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        "active-quote",
                        expectedQuoteUpdate("INITIAL", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of("The initial state should properly be stored.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.INITIAL),
                        setActiveQuote(ContractNegotiationStates.INITIAL, "active-quote", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-quote",
                        expectedQuoteUpdate("INITIAL", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.INITIAL),
                        setActiveQuote(ContractNegotiationStates.INITIAL, "active-quote", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        "active-quote",
                        expectedQuoteUpdate("INITIAL", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of("The initial state should properly be stored.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.REQUESTING),
                        setActiveQuote(ContractNegotiationStates.REQUESTING, "active-quote", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-quote",
                        expectedQuoteUpdate("REQUESTING", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.REQUESTING),
                        setActiveQuote(ContractNegotiationStates.REQUESTING, "active-quote", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        "active-quote",
                        expectedQuoteUpdate("REQUESTING", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of("The initial state should properly be stored.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTING),
                        setActiveQuote(ContractNegotiationStates.REQUESTING, "active-quote", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-quote",
                        expectedQuoteUpdate("REQUESTING", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTING),
                        setActiveQuote(ContractNegotiationStates.REQUESTING, "active-quote", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        "active-quote",
                        expectedQuoteUpdate("REQUESTING", QuoteStateTypeVO.IN_PROGRESS, List.of(getQuoteItem("123"), getQuoteItem("124")))
                )
        );
    }

    private static Stream<Arguments> getInitialStates_create() {
        return Stream.of(
                Arguments.of("The initial state should properly be stored.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.INITIAL),
                        List.of(),
                        List.of(getQuoteItem("123")),
                        expectedQuoteCreate("INITIAL", List.of(getQuoteItem("123")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.INITIAL),
                        List.of(),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        expectedQuoteCreate("INITIAL", List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of("The initial state should properly be stored.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.INITIAL),
                        List.of(),
                        List.of(getQuoteItem("123")),
                        expectedQuoteCreate("INITIAL", List.of(getQuoteItem("123")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.INITIAL),
                        List.of(),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        expectedQuoteCreate("INITIAL", List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of("The initial state should properly be stored.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.REQUESTING),
                        List.of(),
                        List.of(getQuoteItem("123")),
                        expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.CONSUMER, ContractNegotiationStates.REQUESTING),
                        List.of(),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of("The initial state should properly be stored.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTING),
                        List.of(),
                        List.of(getQuoteItem("123")),
                        expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTING),
                        List.of(),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTING),
                        setActiveQuote(ContractNegotiationStates.OFFERING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of("The should be stored with all offers included as quote items.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTING),
                        setActiveQuote(ContractNegotiationStates.OFFERED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123"), getQuoteItem("124")),
                        expectedQuoteCreate("REQUESTING", List.of(getQuoteItem("123"), getQuoteItem("124")))
                )
        );
    }

    // ----- REQUESTED -----

    @ParameterizedTest(name = "{0}")
    @MethodSource("getRequestStates")
    public void testRequestSave_success(String name, ContractNegotiation contractNegotiation, List<ExtendableQuoteVO> negotiationQuotes, List<ExtendableQuoteItemVO> quoteItems, String expectedQuoteId, boolean isCounterOffer, ExtendableQuoteUpdateVO expectedUpdate) {

        testSave_success(contractNegotiation, negotiationQuotes, quoteItems, expectedQuoteId, isCounterOffer, expectedUpdate);
    }

    private void testSave_success(ContractNegotiation contractNegotiation, List<ExtendableQuoteVO> negotiationQuotes, List<ExtendableQuoteItemVO> quoteItems, String expectedQuoteId, boolean isCounterOffer, ExtendableQuoteUpdateVO expectedUpdate) {
        Map<String, List<ExtendableQuoteVO>> quotesByNeg = new HashMap<>();
        negotiationQuotes.forEach(nq -> {
            if (quotesByNeg.containsKey(nq.getExternalId())) {
                quotesByNeg.get(nq.getExternalId()).add(nq);
            } else {
                quotesByNeg.put(nq.getExternalId(), new ArrayList<>(List.of(nq)));
            }
        });
        quotesByNeg.forEach((key, value) -> when(quoteApiClient.findByNegotiationId(eq(key))).thenReturn(value));

        ArgumentCaptor<ExtendableQuoteUpdateVO> quoteCaptor = ArgumentCaptor.forClass(ExtendableQuoteUpdateVO.class);
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);

        ExtendableProductOffering extendableProductOffering = getExtendableProductOffering();

        when(productCatalogApiClient.getProductOfferingByExternalId(eq(TEST_OFFER_ID)))
                .thenReturn(Optional.of(extendableProductOffering));
        switch (contractNegotiation.getType()) {
            case PROVIDER -> quoteItems.forEach(qi -> when(tmfEdcMapper.fromProviderContractOffer(any(), any(), any())).thenReturn(qi));
            case CONSUMER -> quoteItems.forEach(qi -> when(tmfEdcMapper.fromConsumerContractOffer(any(), any())).thenReturn(qi));
        }

        when(tmfEdcMapper.toUpdate(any(ExtendableQuoteVO.class))).thenAnswer(invocation -> {
            ExtendableQuoteVO extendableQuoteVO = invocation.getArgument(0);
            return new TMFObjectMapperImpl().map(extendableQuoteVO);
        });

        when(quoteApiClient.createQuote(any(ExtendableQuoteCreateVO.class))).thenAnswer(invocation -> {
            ExtendableQuoteCreateVO extendableQuoteCreateVO = invocation.getArgument(0);
            ExtendableQuoteVO extendableQuoteVO = new ExtendableQuoteVO()
                    .setContractNegotiationState(extendableQuoteCreateVO.getContractNegotiationState());
            extendableQuoteVO.setQuoteItem(extendableQuoteCreateVO.getQuoteItem());
            extendableQuoteVO.setId(NEW_QUOTE_ID);
            extendableQuoteVO.setExternalId(TEST_NEGOTIATION_ID);
            return extendableQuoteVO;
        });

        tmfBackedContractNegotiationStore.save(contractNegotiation);

        if (isCounterOffer) {
            verify(quoteApiClient, times(2)).updateQuote(idCaptor.capture(), quoteCaptor.capture());
        } else {
            verify(quoteApiClient, times(1)).updateQuote(idCaptor.capture(), quoteCaptor.capture());
        }
        verify(leaseHolder, times(1)).acquireLease(eq(contractNegotiation.getId()), any());
        verify(leaseHolder, times(1)).freeLease(eq(contractNegotiation.getId()), any());

        ExtendableQuoteUpdateVO extendableQuoteUpdateVO = quoteCaptor.getAllValues().getLast();

        assertEquals(expectedQuoteId, idCaptor.getValue(), "The correct quote should have been updated.");

        assertEquals(expectedUpdate.getExternalId(), extendableQuoteUpdateVO.getExternalId(), "The correct negotiationId should be included.");
        assertEquals(expectedUpdate.getContractNegotiationState(), extendableQuoteUpdateVO.getContractNegotiationState(), "The correct negotiation state should be stored.");
        assertEquals(expectedUpdate.getState(), extendableQuoteUpdateVO.getState(), "The quote state should be stored.");
        assertEquals(expectedUpdate.getQuoteItem(), extendableQuoteUpdateVO.getExtendableQuoteItem(), "The offerid should be set for the quote item.");
    }

    private static Stream<Arguments> getRequestStates() {
        return Stream.of(
                Arguments.of(
                        "The negotiation should be transitioned from initial.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED),
                        setActiveQuote(ContractNegotiationStates.INITIAL, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from initial, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED),
                        setActiveQuote(ContractNegotiationStates.INITIAL, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
//                Arguments.of(
//                        "The negotiation should be transitioned from requested.",
//                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED),
//                        setActiveQuote(ContractNegotiationStates.REQUESTED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
//                        List.of(getQuoteItem("123")),
//                        "active-id",
//                        false,
//                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))
//                ),
//                Arguments.of(
//                        "The negotiation should be transitioned from requested, only new offers should be added.",
//                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED),
//                        setActiveQuote(ContractNegotiationStates.REQUESTED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
//                        List.of(getQuoteItem("123")),
//                        "active-id",
//                        false,
//                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123"), getQuoteItem("124")))
//                ),
                Arguments.of(
                        "The negotiation should be transitioned from requesting.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED),
                        setActiveQuote(ContractNegotiationStates.REQUESTING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from requesting, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED),
                        setActiveQuote(ContractNegotiationStates.REQUESTING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from offered.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED),
                        setActiveQuote(ContractNegotiationStates.OFFERED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        NEW_QUOTE_ID,
                        true,
                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from offered, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.REQUESTED),
                        setActiveQuote(ContractNegotiationStates.OFFERED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        NEW_QUOTE_ID,
                        true,
                        expectedQuoteUpdate("REQUESTED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                )
        );
    }

    // ----- OFFER ----
    @ParameterizedTest(name = "{0}")
    @MethodSource("getOfferStates")
    public void testOfferSave_success(String name, ContractNegotiation contractNegotiation, List<ExtendableQuoteVO> negotiationQuotes, List<ExtendableQuoteItemVO> quoteItems, String expectedQuoteId, boolean isCounterOffer, ExtendableQuoteUpdateVO expectedUpdate) {

        testSave_success(contractNegotiation, negotiationQuotes, quoteItems, expectedQuoteId, isCounterOffer, expectedUpdate);
    }

    private static Stream<Arguments> getOfferStates() {
        return Stream.of(
                Arguments.of(
                        "The negotiation should be transitioned from initial.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.OFFERED),
                        setActiveQuote(ContractNegotiationStates.INITIAL, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from initial, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.OFFERED),
                        setActiveQuote(ContractNegotiationStates.INITIAL, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from requested.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.OFFERED),
                        setActiveQuote(ContractNegotiationStates.REQUESTED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from requested, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.OFFERED),
                        setActiveQuote(ContractNegotiationStates.REQUESTED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from offering.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.OFFERED),
                        setActiveQuote(ContractNegotiationStates.OFFERING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from offering, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.OFFERED),
                        setActiveQuote(ContractNegotiationStates.OFFERING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of(
                        "The negotiation should be created in offered.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.OFFERED),
                        List.of(),
                        List.of(getQuoteItem("123")),
                        NEW_QUOTE_ID,
                        false,
                        expectedQuoteUpdate("OFFERED", QuoteStateTypeVO.APPROVED, List.of(getQuoteItem("123")))
                )
        );
    }


    // ----- ACCEPT ----

    @ParameterizedTest(name = "{0}")
    @MethodSource("getAcceptStates")
    public void testAcceptSave_success(String name, ContractNegotiation contractNegotiation, List<ExtendableQuoteVO> negotiationQuotes, List<ExtendableQuoteItemVO> quoteItems, String expectedQuoteId, boolean isCounterOffer, ExtendableQuoteUpdateVO expectedUpdate) {

        testSave_success(contractNegotiation, negotiationQuotes, quoteItems, expectedQuoteId, isCounterOffer, expectedUpdate);
    }

    @ParameterizedTest
    @EnumSource(value = ContractNegotiationStates.class, names = {"ACCEPTED", "ACCEPTING"})
    public void testAcceptSave_terminating(ContractNegotiationStates negotiationState) {
        Map<String, List<ExtendableQuoteVO>> quotesByNeg = new HashMap<>();
        setActiveQuote(ContractNegotiationStates.TERMINATING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5))
                .forEach(nq -> {
                    if (quotesByNeg.containsKey(nq.getExternalId())) {
                        quotesByNeg.get(nq.getExternalId()).add(nq);
                    } else {
                        quotesByNeg.put(nq.getExternalId(), new ArrayList<>(List.of(nq)));
                    }
                });
        quotesByNeg.forEach((key, value) -> when(quoteApiClient.findByNegotiationId(eq(key))).thenReturn(value));


        tmfBackedContractNegotiationStore.save(
                getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, negotiationState));


        // nothing should happen while terminating.
        verify(quoteApiClient, times(0)).updateQuote(any(), any());
        verify(quoteApiClient, times(0)).createQuote(any());

    }

    private static Stream<Arguments> getAcceptStates() {
        return Stream.of(
                Arguments.of(
                        "The negotiation should be transitioned from offered.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTED),
                        setActiveQuote(ContractNegotiationStates.OFFERED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("ACCEPTED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from initial, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTED),
                        setActiveQuote(ContractNegotiationStates.OFFERED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("ACCEPTED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from accepting.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTED),
                        setActiveQuote(ContractNegotiationStates.ACCEPTING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("ACCEPTED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from accepting, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTED),
                        setActiveQuote(ContractNegotiationStates.ACCEPTING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("ACCEPTED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of(
                        "No active quote and no termination -> create.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTED),
                        List.of(),
                        List.of(getQuoteItem("123")),
                        NEW_QUOTE_ID,
                        false,
                        expectedQuoteUpdate("ACCEPTED", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from offered.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTING),
                        setActiveQuote(ContractNegotiationStates.OFFERED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("ACCEPTING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from initial, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTING),
                        setActiveQuote(ContractNegotiationStates.OFFERED, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("ACCEPTING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from accepting.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTING),
                        setActiveQuote(ContractNegotiationStates.ACCEPTING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("ACCEPTING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))
                ),
                Arguments.of(
                        "The negotiation should be transitioned from accepting, only new offers should be added.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123"), getOffer(TEST_OFFER_ID + ":asset:124")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTING),
                        setActiveQuote(ContractNegotiationStates.ACCEPTING, "active-id", getQuotes(TEST_NEGOTIATION_ID, TEST_CONTROL_PLANE_ID, 5)),
                        List.of(getQuoteItem("123")),
                        "active-id",
                        false,
                        expectedQuoteUpdate("ACCEPTING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123"), getQuoteItem("124")))
                ),
                Arguments.of(
                        "No active quote and no termination -> create.",
                        getValidNegotiationWithIdOfferAndType(TEST_NEGOTIATION_ID, List.of(getOffer(TEST_OFFER_ID + ":asset:123")), ContractNegotiation.Type.PROVIDER, ContractNegotiationStates.ACCEPTING),
                        List.of(),
                        List.of(getQuoteItem("123")),
                        NEW_QUOTE_ID,
                        false,
                        expectedQuoteUpdate("ACCEPTING", QuoteStateTypeVO.ACCEPTED, List.of(getQuoteItem("123")))
                )
        );
    }

    private static ExtendableQuoteItemVO getQuoteItem(String externalId) {
        return new ExtendableQuoteItemVO()
                .setExternalId(externalId);
    }

    private static ExtendableQuoteCreateVO expectedQuoteCreate(String state, List<QuoteItemVO> quoteItemVOS) {
        ContractNegotiationState expectedState = new ContractNegotiationState()
                .setState(state)
                .setControlplane(TEST_CONTROL_PLANE)
                .setCounterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
                .setLeased(false)
                .setPending(false);

        ExtendableQuoteCreateVO expectedCreate = new ExtendableQuoteCreateVO().setContractNegotiationState(expectedState);
        expectedCreate.setQuoteItem(quoteItemVOS);
        expectedCreate.setExternalId(TEST_NEGOTIATION_ID);
        return expectedCreate;
    }


    private static ExtendableQuoteUpdateVO expectedQuoteUpdate(String state, QuoteStateTypeVO quoteState, List<QuoteItemVO> quoteItemVOS) {
        ContractNegotiationState expectedState = new ContractNegotiationState()
                .setState(state)
                .setControlplane(TEST_CONTROL_PLANE)
                .setCounterPartyAddress(TEST_COUNTER_PARTY_ADDRESS)
                .setLeased(false)
                .setPending(false);

        ExtendableQuoteUpdateVO expectedUpdate = new ExtendableQuoteUpdateVO().setContractNegotiationState(expectedState);
        expectedUpdate.setQuoteItem(quoteItemVOS);
        expectedUpdate.setState(quoteState);
        expectedUpdate.setExternalId(TEST_NEGOTIATION_ID);
        return expectedUpdate;
    }

    private static List<ExtendableQuoteVO> setActiveQuote(ContractNegotiationStates activeState, String activeId, List<ExtendableQuoteVO> quoteVOS) {
        quoteVOS.forEach(qvo -> qvo.getContractNegotiationState().setState(ContractNegotiationStates.TERMINATED.name()));
        quoteVOS.getLast().getContractNegotiationState().setState(activeState.name());
        quoteVOS.getLast().setId(activeId);
        return quoteVOS;
    }

}
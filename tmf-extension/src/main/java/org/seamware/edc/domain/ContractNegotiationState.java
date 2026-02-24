package org.seamware.edc.domain;

import java.util.Objects;

public class ContractNegotiationState {

    private boolean isPending;
    private boolean isLeased;
    private String controlplane;
    private String state;
    private String correlationId;
    private String counterPartyAddress;

    public boolean isPending() {
        return isPending;
    }

    public String getState() {
        return state;
    }

    public ContractNegotiationState setState(String state) {
        this.state = state;
        return this;
    }

    public ContractNegotiationState setPending(boolean pending) {
        isPending = pending;
        return this;
    }

    public boolean isLeased() {
        return isLeased;
    }

    public ContractNegotiationState setLeased(boolean leased) {
        isLeased = leased;
        return this;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public ContractNegotiationState setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public String getControlplane() {
        return controlplane;
    }

    public ContractNegotiationState setControlplane(String controlplane) {
        this.controlplane = controlplane;
        return this;
    }

    public String getCounterPartyAddress() {
        return counterPartyAddress;
    }

    public ContractNegotiationState setCounterPartyAddress(String counterPartyAddress) {
        this.counterPartyAddress = counterPartyAddress;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractNegotiationState that = (ContractNegotiationState) o;
        return isPending == that.isPending && isLeased == that.isLeased && Objects.equals(controlplane, that.controlplane) && Objects.equals(state, that.state) && Objects.equals(correlationId, that.correlationId) && Objects.equals(counterPartyAddress, that.counterPartyAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isPending, isLeased, controlplane, state, correlationId, counterPartyAddress);
    }
}

package org.seamware.edc.store;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.Lease;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class HashMapLeaseHolder implements LeaseHolder {

    private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(60);

    private final Monitor monitor;
    private final Clock clock;
    private final Map<String, Lease> leases = new HashMap<>();

    public HashMapLeaseHolder(Monitor monitor, Clock clock) {
        this.monitor = monitor;
        this.clock = clock;
    }

    @Override
    public void acquireLease(String id, String lockId, Duration leaseTime) {
        if (!isLeased(id) || isLeasedBy(id, lockId)) {
            monitor.info("Acquire lease " + id + " - " + lockId);
            leases.put(id, new Lease(lockId, clock.millis(), leaseTime.toMillis()));
        } else {
            throw new IllegalStateException("Cannot acquire lease, is already leased by someone else!");
        }
    }

    @Override
    public boolean isLeasedBy(String id, String lockId) {
        synchronized (leases) {
            return isLeased(id) && leases.get(id).getLeasedBy().equals(lockId);
        }
    }

    @Override
    public void freeLease(String id, String reason) {
        synchronized (leases) {
            monitor.info("Free lease " + id + " because " + reason);
            leases.remove(id);
        }
    }

    @Override
    public void acquireLease(String id, String lockId) {
        acquireLease(id, lockId, DEFAULT_LEASE_TIME);
    }

    @Override
    public boolean isLeased(String id) {
        synchronized (leases) {
            return leases.containsKey(id) && !leases.get(id).isExpired(clock.millis());
        }
    }
}

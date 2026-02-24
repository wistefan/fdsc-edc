package org.seamware.edc.store;

import java.time.Duration;

public interface LeaseHolder {

    void acquireLease(String id, String lockId, Duration leaseTime);

    boolean isLeasedBy(String id, String lockId);

    void freeLease(String id, String reason);

    void acquireLease(String id, String lockId);

    boolean isLeased(String id);

}

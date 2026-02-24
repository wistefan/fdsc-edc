package org.seamware.edc.store;

import java.util.function.Supplier;

public interface LockManager {
    <T> T readLock(Supplier<T> work);

    <T> T writeLock(Supplier<T> work);
}

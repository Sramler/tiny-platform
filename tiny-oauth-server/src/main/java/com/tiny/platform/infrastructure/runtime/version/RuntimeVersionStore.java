package com.tiny.platform.infrastructure.runtime.version;

import java.util.Optional;

public interface RuntimeVersionStore {

    Optional<RuntimeVersionSnapshot> find(RuntimeVersionKey key);

    RuntimeVersionSnapshot initializeIfAbsent(RuntimeVersionKey key,
                                              String versionValue,
                                              String reason,
                                              Long actorUserId);

    RuntimeVersionSnapshot bump(RuntimeVersionKey key, String reason, Long actorUserId);
}

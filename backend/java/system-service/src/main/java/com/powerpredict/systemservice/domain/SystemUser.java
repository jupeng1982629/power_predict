package com.powerpredict.systemservice.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record SystemUser(
    String userId,
    String userName,
    String displayName,
    String tenantId,
    boolean enabled,
    List<String> roleIds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

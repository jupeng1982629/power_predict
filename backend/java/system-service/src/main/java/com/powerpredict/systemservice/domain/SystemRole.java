package com.powerpredict.systemservice.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record SystemRole(
    String roleId,
    String roleName,
    String description,
    List<String> permissionCodes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

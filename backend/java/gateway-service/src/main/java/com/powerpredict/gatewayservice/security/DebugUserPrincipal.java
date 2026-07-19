package com.powerpredict.gatewayservice.security;

import java.util.List;

public record DebugUserPrincipal(
    String userId,
    String userName,
    String tenantId,
    List<String> roles
) {
}
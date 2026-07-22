package com.powerpredict.systemservice.api;

import com.powerpredict.common.api.ApiResponse;
import com.powerpredict.systemservice.security.DebugUserPrincipal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class DemoController {
  @GetMapping("/session")
  public ApiResponse<Map<String, Object>> session(
      Authentication authentication,
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestHeader(value = "X-User-Name", required = false) String userName,
      @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
      @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
    SessionIdentity identity = resolveIdentity(authentication, userId, userName, tenantId, rolesHeader);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("userId", identity.userId());
    data.put("userName", identity.userName());
    data.put("tenantId", identity.tenantId());
    data.put("roles", identity.roles());
    data.put("permissions", identity.roles());
    data.put("authMode", identity.authMode());
    data.put("issuedAt", OffsetDateTime.now().toString());
    return ApiResponse.ok(data);
  }

  private SessionIdentity resolveIdentity(
      Authentication authentication,
      String headerUserId,
      String headerUserName,
      String headerTenantId,
      String headerRoles) {
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      Jwt jwt = jwtAuthenticationToken.getToken();
      List<String> roles = extractJwtRoles(jwt);
      return new SessionIdentity(
          fallback(jwt.getClaimAsString("sub"), "user-unknown"),
          fallback(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("sub"), "unknown"),
          fallback(jwt.getClaimAsString("tenant_id"), "tenant-default"),
          roles,
          "jwt-resource-server");
    }

    if (authentication != null && authentication.getPrincipal() instanceof DebugUserPrincipal principal) {
      return new SessionIdentity(
          principal.userId(),
          principal.userName(),
          principal.tenantId(),
          principal.roles(),
          "local-debug-token");
    }

    List<String> roles = StringUtils.hasText(headerRoles)
        ? Arrays.stream(headerRoles.split(",")).map(String::trim).filter(StringUtils::hasText).toList()
        : List.of("forecast:read", "forecast:run", "system:admin", "plant:read");

    return new SessionIdentity(
        fallback(headerUserId, "user-demo-admin"),
        fallback(headerUserName, "Demo Admin"),
        fallback(headerTenantId, "tenant-demo"),
        roles,
        "header-fallback");
  }

  private List<String> extractJwtRoles(Jwt jwt) {
    Object realmAccess = jwt.getClaim("realm_access");
    if (realmAccess instanceof Map<?, ?> map) {
      Object roles = map.get("roles");
      if (roles instanceof List<?> roleList) {
        List<String> parsed = roleList.stream().map(String::valueOf).filter(StringUtils::hasText).toList();
        if (!parsed.isEmpty()) {
          return parsed;
        }
      }
    }

    String scopeClaim = jwt.getClaimAsString("scope");
    if (StringUtils.hasText(scopeClaim)) {
      return Arrays.stream(scopeClaim.split(" ")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    return List.of("authenticated");
  }

  private String fallback(String value, String defaultValue) {
    return StringUtils.hasText(value) ? value : defaultValue;
  }

  private String fallback(String value, String secondaryValue, String defaultValue) {
    if (StringUtils.hasText(value)) {
      return value;
    }
    if (StringUtils.hasText(secondaryValue)) {
      return secondaryValue;
    }
    return defaultValue;
  }

  private record SessionIdentity(String userId, String userName, String tenantId, List<String> roles, String authMode) {
  }
}
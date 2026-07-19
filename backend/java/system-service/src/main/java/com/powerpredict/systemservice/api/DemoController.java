package com.powerpredict.systemservice.api;

import com.powerpredict.common.api.ApiResponse;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestHeader(value = "X-User-Name", required = false) String userName,
      @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
      @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
    List<String> roles = StringUtils.hasText(rolesHeader)
        ? Arrays.stream(rolesHeader.split(",")).map(String::trim).filter(StringUtils::hasText).toList()
        : List.of("forecast:read", "forecast:run", "system:admin", "plant:read");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("userId", StringUtils.hasText(userId) ? userId : "user-demo-admin");
    data.put("userName", StringUtils.hasText(userName) ? userName : "Demo Admin");
    data.put("tenantId", StringUtils.hasText(tenantId) ? tenantId : "tenant-demo");
    data.put("roles", roles);
    data.put("permissions", roles);
    data.put("authMode", "local-debug-stage4");
    data.put("issuedAt", OffsetDateTime.now().toString());
    return ApiResponse.ok(data);
  }
}
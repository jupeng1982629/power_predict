package com.powerpredict.gatewayservice.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerpredict.gatewayservice.config.PowerPredictGatewayProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemGatewayController {
  private final GatewayProxySupport proxySupport;
  private final ObjectMapper objectMapper;
  private final String baseUrl;

  public SystemGatewayController(
      PowerPredictGatewayProperties properties,
      GatewayProxySupport proxySupport,
      ObjectMapper objectMapper) {
    this.proxySupport = proxySupport;
    this.objectMapper = objectMapper;
    this.baseUrl = properties.getSystemService().getBaseUrl();
  }

  @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> users(
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "keyword", required = false) String keyword) {
    StringBuilder uri = new StringBuilder("/api/v1/system/users?pageNo=").append(pageNo)
        .append("&pageSize=").append(pageSize);
    if (keyword != null && !keyword.isBlank()) {
      uri.append("&keyword=").append(encode(keyword));
    }
    return proxySupport.getJson(baseUrl, uri.toString());
  }

  @PostMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> createUser(@RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.postJson(baseUrl, "/api/v1/system/users", objectMapper.writeValueAsString(payload));
  }

  @PutMapping(value = "/users/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> updateUser(
      @PathVariable("userId") String userId,
      @RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.putJson(baseUrl, "/api/v1/system/users/" + encodePath(userId), objectMapper.writeValueAsString(payload));
  }

  @DeleteMapping(value = "/users/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> deleteUser(@PathVariable("userId") String userId) {
    return proxySupport.deleteJson(baseUrl, "/api/v1/system/users/" + encodePath(userId));
  }

  @GetMapping(value = "/roles", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> roles(
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "keyword", required = false) String keyword) {
    StringBuilder uri = new StringBuilder("/api/v1/system/roles?pageNo=").append(pageNo)
        .append("&pageSize=").append(pageSize);
    if (keyword != null && !keyword.isBlank()) {
      uri.append("&keyword=").append(encode(keyword));
    }
    return proxySupport.getJson(baseUrl, uri.toString());
  }

  @PostMapping(value = "/roles", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> createRole(@RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.postJson(baseUrl, "/api/v1/system/roles", objectMapper.writeValueAsString(payload));
  }

  @PutMapping(value = "/roles/{roleId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> updateRole(
      @PathVariable("roleId") String roleId,
      @RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.putJson(baseUrl, "/api/v1/system/roles/" + encodePath(roleId), objectMapper.writeValueAsString(payload));
  }

  @DeleteMapping(value = "/roles/{roleId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> deleteRole(@PathVariable("roleId") String roleId) {
    return proxySupport.deleteJson(baseUrl, "/api/v1/system/roles/" + encodePath(roleId));
  }

  @GetMapping(value = "/permissions", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> permissions() {
    return proxySupport.getJson(baseUrl, "/api/v1/system/permissions");
  }

  @PutMapping(value = "/roles/{roleId}/permissions", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> bindPermissions(
      @PathVariable("roleId") String roleId,
      @RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.putJson(baseUrl, "/api/v1/system/roles/" + encodePath(roleId) + "/permissions", objectMapper.writeValueAsString(payload));
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String encodePath(String value) {
    return value.replace(" ", "%20");
  }
}

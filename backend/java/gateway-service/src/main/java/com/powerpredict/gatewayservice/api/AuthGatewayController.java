package com.powerpredict.gatewayservice.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/auth")
public class AuthGatewayController {
  private final String systemServiceBaseUrl;
  private final GatewayProxySupport proxySupport;

  public AuthGatewayController(
      @Value("${powerpredict.system-service.base-url:http://localhost:8081}") String systemServiceBaseUrl,
      GatewayProxySupport proxySupport) {
    this.systemServiceBaseUrl = systemServiceBaseUrl;
    this.proxySupport = proxySupport;
  }

  @GetMapping(value = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> session() {
    return proxySupport.getJson(systemServiceBaseUrl, "/api/v1/auth/session");
  }
}
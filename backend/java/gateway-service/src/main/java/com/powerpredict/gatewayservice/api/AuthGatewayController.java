package com.powerpredict.gatewayservice.api;

import com.powerpredict.gatewayservice.config.PowerPredictGatewayProperties;
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
      PowerPredictGatewayProperties properties,
      GatewayProxySupport proxySupport) {
    this.systemServiceBaseUrl = properties.getSystemService().getBaseUrl();
    this.proxySupport = proxySupport;
  }

  @GetMapping(value = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> session() {
    return proxySupport.getJson(systemServiceBaseUrl, "/api/v1/auth/session");
  }
}
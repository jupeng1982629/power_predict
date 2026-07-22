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
@RequestMapping("/api/v1/plants")
public class PlantGatewayController {
  private final GatewayProxySupport proxySupport;
  private final ObjectMapper objectMapper;
  private final String baseUrl;

  public PlantGatewayController(
      PowerPredictGatewayProperties properties,
      GatewayProxySupport proxySupport,
      ObjectMapper objectMapper) {
    this.proxySupport = proxySupport;
    this.objectMapper = objectMapper;
    this.baseUrl = properties.getPlantService().getBaseUrl();
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> list(
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "keyword", required = false) String keyword,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "timezone", required = false) String timezone,
      @RequestParam(name = "sortBy", required = false) String sortBy,
      @RequestParam(name = "sortDir", required = false) String sortDir) {
    StringBuilder uri = new StringBuilder("/api/v1/plants?pageNo=").append(pageNo)
        .append("&pageSize=").append(pageSize);
    if (keyword != null && !keyword.isBlank()) {
      uri.append("&keyword=").append(encode(keyword));
    }
    if (status != null && !status.isBlank()) {
      uri.append("&status=").append(encode(status));
    }
    if (timezone != null && !timezone.isBlank()) {
      uri.append("&timezone=").append(encode(timezone));
    }
    if (sortBy != null && !sortBy.isBlank()) {
      uri.append("&sortBy=").append(encode(sortBy));
    }
    if (sortDir != null && !sortDir.isBlank()) {
      uri.append("&sortDir=").append(encode(sortDir));
    }
    return proxySupport.getJson(baseUrl, uri.toString());
  }

  @GetMapping(value = "/{plantId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> get(@PathVariable("plantId") String plantId) {
    return proxySupport.getJson(baseUrl, "/api/v1/plants/" + encodePath(plantId));
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> create(@RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.postJson(baseUrl, "/api/v1/plants", objectMapper.writeValueAsString(payload));
  }

  @PutMapping(value = "/{plantId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> update(@PathVariable("plantId") String plantId, @RequestBody Map<String, Object> payload)
      throws JsonProcessingException {
    return proxySupport.putJson(baseUrl, "/api/v1/plants/" + encodePath(plantId), objectMapper.writeValueAsString(payload));
  }

  @DeleteMapping(value = "/{plantId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> delete(@PathVariable("plantId") String plantId) {
    return proxySupport.deleteJson(baseUrl, "/api/v1/plants/" + encodePath(plantId));
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String encodePath(String value) {
    return value.replace(" ", "%20");
  }
}

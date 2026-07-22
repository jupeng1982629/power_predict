package com.powerpredict.gatewayservice.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerpredict.gatewayservice.config.PowerPredictGatewayProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/forecast")
public class ForecastOrchestrationGatewayController {
  private final GatewayProxySupport proxySupport;
  private final ObjectMapper objectMapper;
  private final String baseUrl;

  public ForecastOrchestrationGatewayController(
      PowerPredictGatewayProperties properties,
      GatewayProxySupport proxySupport,
      ObjectMapper objectMapper) {
    this.proxySupport = proxySupport;
    this.objectMapper = objectMapper;
    this.baseUrl = properties.getForecastService().getBaseUrl();
  }

  @PostMapping(value = "/model-train-jobs", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> submitTrain(@RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.postJson(baseUrl, "/api/v1/forecast/model-train-jobs", objectMapper.writeValueAsString(payload));
  }

  @GetMapping(value = "/model-train-jobs/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> trainJob(@PathVariable("jobId") String jobId) {
    return proxySupport.getJson(baseUrl, "/api/v1/forecast/model-train-jobs/" + encodePath(jobId));
  }

  @GetMapping(value = "/plants/{plantId}/models", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> models(@PathVariable("plantId") String plantId) {
    return proxySupport.getJson(baseUrl, "/api/v1/forecast/plants/" + encodePath(plantId) + "/models");
  }

  @PutMapping(value = "/plants/{plantId}/models/{modelVersion}:promote", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> promote(
      @PathVariable("plantId") String plantId,
      @PathVariable("modelVersion") String modelVersion) {
    return proxySupport.putJson(baseUrl,
        "/api/v1/forecast/plants/" + encodePath(plantId) + "/models/" + encodePath(modelVersion) + ":promote",
        "{}");
  }

  @PostMapping(value = "/jobs/dayahead", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> submitDayAhead(@RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.postJson(baseUrl, "/api/v1/forecast/jobs/dayahead", objectMapper.writeValueAsString(payload));
  }

  @GetMapping(value = "/jobs/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getDayAheadJob(@PathVariable("jobId") String jobId) {
    return proxySupport.getJson(baseUrl, "/api/v1/forecast/jobs/" + encodePath(jobId));
  }

  @GetMapping(value = "/plants/{plantId}/dayahead", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> listDayAhead(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "forecastDate", required = false) String forecastDate,
      @RequestParam(name = "modelVersion", required = false) String modelVersion) {
    StringBuilder uri = new StringBuilder("/api/v1/forecast/plants/")
        .append(encodePath(plantId))
        .append("/dayahead");
    if ((forecastDate != null && !forecastDate.isBlank()) || (modelVersion != null && !modelVersion.isBlank())) {
      uri.append("?");
      boolean appendAmp = false;
      if (forecastDate != null && !forecastDate.isBlank()) {
        uri.append("forecastDate=").append(encode(forecastDate));
        appendAmp = true;
      }
      if (modelVersion != null && !modelVersion.isBlank()) {
        if (appendAmp) {
          uri.append("&");
        }
        uri.append("modelVersion=").append(encode(modelVersion));
      }
    }
    return proxySupport.getJson(baseUrl, uri.toString());
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String encodePath(String value) {
    return value.replace(" ", "%20");
  }
}

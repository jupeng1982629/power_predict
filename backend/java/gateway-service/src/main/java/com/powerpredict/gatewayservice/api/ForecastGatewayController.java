package com.powerpredict.gatewayservice.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1")
public class ForecastGatewayController {
  private final String inferenceServiceBaseUrl;
  private final GatewayProxySupport proxySupport;
  private final ObjectMapper objectMapper;

  public ForecastGatewayController(
      @Value("${powerpredict.inference-service.base-url:http://localhost:8003}") String inferenceServiceBaseUrl,
      GatewayProxySupport proxySupport,
      ObjectMapper objectMapper) {
    this.inferenceServiceBaseUrl = inferenceServiceBaseUrl;
    this.proxySupport = proxySupport;
    this.objectMapper = objectMapper;
  }

  @GetMapping(value = "/dashboard/overview", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> overview(@RequestParam(name = "plantId", defaultValue = "plant-demo-001") String plantId) {
    String uri = "/api/v1/dashboard/overview?plant_id=" + encode(plantId);
    return proxySupport.getJson(inferenceServiceBaseUrl, uri);
  }

  @GetMapping(value = "/plants/{plantId}/forecasts", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> forecasts(@PathVariable("plantId") String plantId, @RequestParam(name = "forecastDate") String forecastDate) {
    String uri = "/api/v1/plants/" + encodePath(plantId) + "/forecasts?forecast_date=" + encode(forecastDate);
    return proxySupport.getJson(inferenceServiceBaseUrl, uri);
  }

  @GetMapping(value = "/plants/{plantId}/actuals", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> actuals(@PathVariable("plantId") String plantId, @RequestParam(name = "forecastDate") String forecastDate) {
    String uri = "/api/v1/plants/" + encodePath(plantId) + "/actuals?forecast_date=" + encode(forecastDate);
    return proxySupport.getJson(inferenceServiceBaseUrl, uri);
  }

  @GetMapping(value = "/plants/{plantId}/evaluations", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> evaluations(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "startDate", required = false) String startDate,
      @RequestParam(name = "endDate", required = false) String endDate) {
    String uri = "/api/v1/plants/" + encodePath(plantId) + "/evaluations";
    if (startDate != null && !startDate.isBlank()) {
      uri += "?start_date=" + encode(startDate);
      if (endDate != null && !endDate.isBlank()) {
        uri += "&end_date=" + encode(endDate);
      }
    } else if (endDate != null && !endDate.isBlank()) {
      uri += "?end_date=" + encode(endDate);
    }

    return proxySupport.getJson(inferenceServiceBaseUrl, uri);
  }

  @PostMapping(value = "/jobs/forecast-dayahead", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> runForecast(@RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.postJson(
        inferenceServiceBaseUrl,
        "/api/v1/jobs/forecast-dayahead",
        objectMapper.writeValueAsString(payload));
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String encodePath(String value) {
    return value.replace(" ", "%20");
  }
}
package com.powerpredict.gatewayservice.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerpredict.gatewayservice.config.PowerPredictGatewayProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorGatewayController {
  private final GatewayProxySupport proxySupport;
  private final ObjectMapper objectMapper;
  private final String baseUrl;

  public MonitorGatewayController(
      PowerPredictGatewayProperties properties,
      GatewayProxySupport proxySupport,
      ObjectMapper objectMapper) {
    this.proxySupport = proxySupport;
    this.objectMapper = objectMapper;
    this.baseUrl = properties.getMonitorService().getBaseUrl();
  }

  @GetMapping(value = "/plants/{plantId}/realtime", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> realtime(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "at", required = false) String at) {
    String uri = "/api/v1/monitor/plants/" + encodePath(plantId) + "/realtime";
    if (at != null && !at.isBlank()) {
      uri += "?at=" + encode(at);
    }
    return proxySupport.getJson(baseUrl, uri);
  }

  @GetMapping(value = "/plants/{plantId}/timeseries", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> timeseries(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "startTime", required = false) String startTime,
      @RequestParam(name = "endTime", required = false) String endTime,
      @RequestParam(name = "granularity", required = false) String granularity,
      @RequestParam(name = "metrics", required = false) List<String> metrics) {
    StringBuilder uri = new StringBuilder("/api/v1/monitor/plants/")
        .append(encodePath(plantId))
        .append("/timeseries");
    List<String> pairs = new java.util.ArrayList<>();
    if (startTime != null && !startTime.isBlank()) {
      pairs.add("startTime=" + encode(startTime));
    }
    if (endTime != null && !endTime.isBlank()) {
      pairs.add("endTime=" + encode(endTime));
    }
    if (granularity != null && !granularity.isBlank()) {
      pairs.add("granularity=" + encode(granularity));
    }
    if (metrics != null) {
      for (String metric : metrics) {
        if (metric != null && !metric.isBlank()) {
          pairs.add("metrics=" + encode(metric));
        }
      }
    }
    if (!pairs.isEmpty()) {
      uri.append("?").append(String.join("&", pairs));
    }
    return proxySupport.getJson(baseUrl, uri.toString());
  }

  @GetMapping(value = "/plants/{plantId}/generation-records", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> listGeneration(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "startTime", required = false) String startTime,
      @RequestParam(name = "endTime", required = false) String endTime) {
    StringBuilder uri = new StringBuilder("/api/v1/monitor/plants/")
        .append(encodePath(plantId))
        .append("/generation-records?pageNo=").append(pageNo)
        .append("&pageSize=").append(pageSize);
    if (startTime != null && !startTime.isBlank()) {
      uri.append("&startTime=").append(encode(startTime));
    }
    if (endTime != null && !endTime.isBlank()) {
      uri.append("&endTime=").append(encode(endTime));
    }
    return proxySupport.getJson(baseUrl, uri.toString());
  }

  @PostMapping(value = "/plants/{plantId}/generation-records", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> createGeneration(@PathVariable("plantId") String plantId, @RequestBody Map<String, Object> payload)
      throws JsonProcessingException {
    return proxySupport.postJson(baseUrl,
        "/api/v1/monitor/plants/" + encodePath(plantId) + "/generation-records",
        objectMapper.writeValueAsString(payload));
  }

  @PutMapping(value = "/plants/{plantId}/generation-records/{recordId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> updateGeneration(
      @PathVariable("plantId") String plantId,
      @PathVariable("recordId") String recordId,
      @RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.putJson(baseUrl,
        "/api/v1/monitor/plants/" + encodePath(plantId) + "/generation-records/" + encodePath(recordId),
        objectMapper.writeValueAsString(payload));
  }

  @DeleteMapping(value = "/plants/{plantId}/generation-records/{recordId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> deleteGeneration(@PathVariable("plantId") String plantId, @PathVariable("recordId") String recordId) {
    return proxySupport.deleteJson(baseUrl,
        "/api/v1/monitor/plants/" + encodePath(plantId) + "/generation-records/" + encodePath(recordId));
  }

  @PostMapping(value = "/plants/{plantId}/generation-records:import", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> importGeneration(
      @PathVariable("plantId") String plantId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(name = "templateVersion", required = false) String templateVersion,
      @RequestParam(name = "timezone", required = false) String timezone,
      @RequestParam(name = "deduplicateStrategy", required = false) String deduplicateStrategy) {
    Map<String, String> fields = new LinkedHashMap<>();
    if (templateVersion != null) {
      fields.put("templateVersion", templateVersion);
    }
    if (timezone != null) {
      fields.put("timezone", timezone);
    }
    if (deduplicateStrategy != null) {
      fields.put("deduplicateStrategy", deduplicateStrategy);
    }
    return proxySupport.postMultipart(baseUrl,
        "/api/v1/monitor/plants/" + encodePath(plantId) + "/generation-records:import",
        file,
        fields);
  }

  @GetMapping(value = "/plants/{plantId}/weather-records", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> listWeather(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "startTime", required = false) String startTime,
      @RequestParam(name = "endTime", required = false) String endTime) {
    StringBuilder uri = new StringBuilder("/api/v1/monitor/plants/")
        .append(encodePath(plantId))
        .append("/weather-records?pageNo=").append(pageNo)
        .append("&pageSize=").append(pageSize);
    if (startTime != null && !startTime.isBlank()) {
      uri.append("&startTime=").append(encode(startTime));
    }
    if (endTime != null && !endTime.isBlank()) {
      uri.append("&endTime=").append(encode(endTime));
    }
    return proxySupport.getJson(baseUrl, uri.toString());
  }

  @PostMapping(value = "/plants/{plantId}/weather-records", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> createWeather(@PathVariable("plantId") String plantId, @RequestBody Map<String, Object> payload)
      throws JsonProcessingException {
    return proxySupport.postJson(baseUrl,
        "/api/v1/monitor/plants/" + encodePath(plantId) + "/weather-records",
        objectMapper.writeValueAsString(payload));
  }

  @PutMapping(value = "/plants/{plantId}/weather-records/{recordId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> updateWeather(
      @PathVariable("plantId") String plantId,
      @PathVariable("recordId") String recordId,
      @RequestBody Map<String, Object> payload) throws JsonProcessingException {
    return proxySupport.putJson(baseUrl,
        "/api/v1/monitor/plants/" + encodePath(plantId) + "/weather-records/" + encodePath(recordId),
        objectMapper.writeValueAsString(payload));
  }

  @DeleteMapping(value = "/plants/{plantId}/weather-records/{recordId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> deleteWeather(@PathVariable("plantId") String plantId, @PathVariable("recordId") String recordId) {
    return proxySupport.deleteJson(baseUrl,
        "/api/v1/monitor/plants/" + encodePath(plantId) + "/weather-records/" + encodePath(recordId));
  }

  @PostMapping(value = "/plants/{plantId}/weather-records:import", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> importWeather(
      @PathVariable("plantId") String plantId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(name = "templateVersion", required = false) String templateVersion,
      @RequestParam(name = "timezone", required = false) String timezone,
      @RequestParam(name = "deduplicateStrategy", required = false) String deduplicateStrategy) {
    Map<String, String> fields = new LinkedHashMap<>();
    if (templateVersion != null) {
      fields.put("templateVersion", templateVersion);
    }
    if (timezone != null) {
      fields.put("timezone", timezone);
    }
    if (deduplicateStrategy != null) {
      fields.put("deduplicateStrategy", deduplicateStrategy);
    }
    return proxySupport.postMultipart(baseUrl,
        "/api/v1/monitor/plants/" + encodePath(plantId) + "/weather-records:import",
        file,
        fields);
  }

  @GetMapping(value = "/import-jobs/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> importJob(@PathVariable("jobId") String jobId) {
    return proxySupport.getJson(baseUrl, "/api/v1/monitor/import-jobs/" + encodePath(jobId));
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String encodePath(String value) {
    return value.replace(" ", "%20");
  }
}

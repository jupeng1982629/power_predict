package com.powerpredict.forecastservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerpredict.forecastservice.config.PowerPredictForecastProperties;
import com.powerpredict.forecastservice.domain.ForecastJob;
import com.powerpredict.forecastservice.domain.ForecastPoint;
import com.powerpredict.forecastservice.domain.ModelVersionSummary;
import com.powerpredict.forecastservice.domain.TrainJob;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InMemoryForecastOrchestrationService implements ForecastOrchestrationService {
  private final Map<String, TrainJob> trainJobs = new LinkedHashMap<>();
  private final Map<String, ForecastJob> forecastJobs = new LinkedHashMap<>();
  private final Map<String, List<ModelVersionSummary>> modelsByPlant = new LinkedHashMap<>();
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper objectMapper;
  private final String featureServiceBaseUrl;
  private final String modelServiceBaseUrl;
  private final String inferenceServiceBaseUrl;

  public InMemoryForecastOrchestrationService(
      PowerPredictForecastProperties properties,
      ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.featureServiceBaseUrl = properties.getFeatureService().getBaseUrl();
    this.modelServiceBaseUrl = properties.getModelService().getBaseUrl();
    this.inferenceServiceBaseUrl = properties.getInferenceService().getBaseUrl();
    seed();
  }

  @Override
  public synchronized TrainJob submitTrainJob(TrainJobCommand command) {
    String plantId = valueOr(command.plantId(), "plant-demo-001");
    LocalDate startDate = command.startDate() == null ? LocalDate.now().minusDays(30) : command.startDate();
    LocalDate endDate = command.endDate() == null ? LocalDate.now().minusDays(1) : command.endDate();
    String algorithm = valueOr(command.algorithm(), "xgboost");
    String featureVersion = valueOr(command.featureSetVersion(), "feature-v1");

    OffsetDateTime startTime = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime endTimeExclusive = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

    JsonNode featureResponse = postJson(featureServiceBaseUrl, "/internal/v1/features/training-dataset", Map.of(
      "plantId", plantId,
      "startTime", startTime,
      "endTime", endTimeExclusive,
      "featureSetVersion", featureVersion));
    JsonNode featureData = responseData(featureResponse);
    String datasetUri = textAt(featureData, "datasetUri", "memory://feature-service/training/" + plantId + "/latest.parquet");
    String resolvedFeatureVersion = textAt(featureData, "featureVersion", featureVersion);

    JsonNode accepted = postJson(modelServiceBaseUrl, "/internal/v1/models/train", Map.of(
      "plantId", plantId,
      "datasetUri", datasetUri,
      "algorithm", algorithm,
      "featureVersion", resolvedFeatureVersion));
    JsonNode acceptedData = responseData(accepted);
    String trainingJobId = textAt(acceptedData, "trainingJobId", "train-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));

    JsonNode statusResponse = getJson(modelServiceBaseUrl, "/internal/v1/models/train/" + encodePath(trainingJobId));
    JsonNode statusData = responseData(statusResponse);
    String statusRaw = textAt(statusData, "status", "submitted");
    String modelVersion = textAt(statusData, "modelVersion", textAt(acceptedData, "modelVersion", trainingJobId));
    String normalizedStatus = normalizeStatus(statusRaw);

    OffsetDateTime now = OffsetDateTime.now();
    String jobId = trainingJobId;
    JsonNode metricsNode = statusData.path("metrics");
    String metricsSummary = metricsToSummary(metricsNode);
    TrainJob job = new TrainJob(
        jobId,
        plantId,
        startDate,
        endDate,
        algorithm,
      resolvedFeatureVersion,
      normalizedStatus,
        modelVersion,
      "Training orchestrated via feature-service and model-service",
        now,
        now);
    trainJobs.put(jobId, job);

    List<ModelVersionSummary> models = modelsByPlant.computeIfAbsent(plantId, key -> new ArrayList<>());
    models.add(0, new ModelVersionSummary(
        plantId,
        modelVersion,
        algorithm,
      resolvedFeatureVersion,
        models.isEmpty() ? "production" : "candidate",
        now,
      metricsSummary));
    models.sort(Comparator.comparing(ModelVersionSummary::trainedAt).reversed());

    return job;
  }

  @Override
  public synchronized TrainJob getTrainJob(String jobId) {
    TrainJob job = trainJobs.get(jobId);
    if (job == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Train job not found: " + jobId);
    }
    return job;
  }

  @Override
  public synchronized List<ModelVersionSummary> listPlantModels(String plantId) {
    return new ArrayList<>(modelsByPlant.getOrDefault(plantId, List.of()));
  }

  @Override
  public synchronized ModelVersionSummary promoteModel(String plantId, String modelVersion) {
    List<ModelVersionSummary> models = modelsByPlant.getOrDefault(plantId, List.of());
    if (models.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No models for plant: " + plantId);
    }

    List<ModelVersionSummary> updated = new ArrayList<>();
    ModelVersionSummary promoted = null;
    for (ModelVersionSummary item : models) {
      if (item.modelVersion().equals(modelVersion)) {
        promoted = new ModelVersionSummary(
            item.plantId(),
            item.modelVersion(),
            item.algorithm(),
            item.featureVersion(),
            "production",
            item.trainedAt(),
            item.metricsSummary());
        updated.add(promoted);
      } else {
        updated.add(new ModelVersionSummary(
            item.plantId(),
            item.modelVersion(),
            item.algorithm(),
            item.featureVersion(),
            "candidate",
            item.trainedAt(),
            item.metricsSummary()));
      }
    }

    if (promoted == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model version not found: " + modelVersion);
    }

    updated.sort(Comparator.comparing(ModelVersionSummary::trainedAt).reversed());
    modelsByPlant.put(plantId, updated);
    return promoted;
  }

  @Override
  public synchronized ForecastJob submitForecastJob(ForecastJobCommand command) {
    String plantId = valueOr(command.plantId(), "plant-demo-001");
    LocalDate forecastDate = command.forecastDate() == null ? LocalDate.now() : command.forecastDate();
    List<ModelVersionSummary> models = modelsByPlant.getOrDefault(plantId, List.of());
    String modelVersion = command.modelVersion();
    if (modelVersion == null || modelVersion.isBlank()) {
      modelVersion = models.stream()
          .filter(m -> "production".equals(m.status()))
          .map(ModelVersionSummary::modelVersion)
          .findFirst()
          .orElse("model-default");
    }

    OffsetDateTime runTime = OffsetDateTime.now();

    JsonNode runResponse = postJson(inferenceServiceBaseUrl, "/internal/v1/inference/dayahead", Map.of(
      "plantId", plantId,
      "forecastDate", forecastDate,
      "modelVersion", modelVersion,
      "weatherSnapshot", Map.of()));
    JsonNode runData = responseData(runResponse);
    String jobId = textAt(runData, "jobId", "forecast-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    String resolvedModelVersion = textAt(runData, "modelVersion", modelVersion);
    String normalizedStatus = normalizeStatus(textAt(runData, "status", "submitted"));

    String forecastPath = "/api/v1/plants/" + encodePath(plantId) + "/forecasts?forecast_date=" + encodeQuery(forecastDate.toString());
    JsonNode forecastResponse = getJson(inferenceServiceBaseUrl, forecastPath);
    JsonNode forecastData = responseData(forecastResponse);
    List<ForecastPoint> points = parseForecastPoints(forecastData.path("points"));

    ForecastJob job = new ForecastJob(
        jobId,
        plantId,
        forecastDate,
      resolvedModelVersion,
        valueOr(command.weatherSource(), "api"),
      normalizedStatus,
        points,
        runTime,
        runTime);
    forecastJobs.put(jobId, job);
    return job;
  }

  @Override
  public synchronized ForecastJob getForecastJob(String jobId) {
    ForecastJob job = forecastJobs.get(jobId);
    if (job == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Forecast job not found: " + jobId);
    }
    return job;
  }

  @Override
  public synchronized List<ForecastJob> listDayAhead(String plantId, LocalDate forecastDate, String modelVersion) {
    List<ForecastJob> result = new ArrayList<>();
    for (ForecastJob job : forecastJobs.values()) {
      if (!job.plantId().equals(plantId)) {
        continue;
      }
      if (forecastDate != null && !job.forecastDate().equals(forecastDate)) {
        continue;
      }
      if (modelVersion != null && !modelVersion.isBlank() && !job.modelVersion().equals(modelVersion)) {
        continue;
      }
      result.add(job);
    }
    result.sort(Comparator.comparing(ForecastJob::updatedAt).reversed());
    return result;
  }

  private String valueOr(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private JsonNode getJson(String baseUrl, String path) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
        .GET()
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/json")
        .build();
    return execute(request);
  }

  private JsonNode postJson(String baseUrl, String path, Object payload) {
    String body;
    try {
      body = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Serialize payload failed", exception);
    }

    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .build();
    return execute(request);
  }

  private JsonNode execute(HttpRequest request) {
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Upstream call failed: " + request.uri() + " status=" + response.statusCode() + " body=" + response.body());
      }
      String raw = response.body() == null || response.body().isBlank() ? "{}" : response.body();
      return objectMapper.readTree(raw);
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream call failed: " + request.uri(), exception);
    }
  }

  private JsonNode responseData(JsonNode root) {
    JsonNode data = root.path("data");
    return data.isMissingNode() || data.isNull() ? root : data;
  }

  private String textAt(JsonNode node, String field, String fallback) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    String result = value.asText();
    return result == null || result.isBlank() ? fallback : result;
  }

  private String metricsToSummary(JsonNode metrics) {
    if (metrics == null || metrics.isMissingNode() || metrics.isNull() || !metrics.isObject()) {
      return "rmse=0.00,mae=0.00,mape=0.00";
    }
    double rmse = metrics.path("rmse").asDouble(0.0);
    double mae = metrics.path("mae").asDouble(0.0);
    double mape = metrics.path("mape").asDouble(0.0);
    return String.format("rmse=%.2f,mae=%.2f,mape=%.2f", rmse, mae, mape);
  }

  private String normalizeStatus(String status) {
    String normalized = status == null ? "SUBMITTED" : status.trim().toUpperCase();
    if (normalized.equals("SUCCESS")) {
      return "SUCCESS";
    }
    if (normalized.equals("FAILED") || normalized.equals("ERROR")) {
      return "FAILED";
    }
    return "RUNNING";
  }

  private List<ForecastPoint> parseForecastPoints(JsonNode pointsNode) {
    List<ForecastPoint> points = new ArrayList<>();
    if (pointsNode == null || !pointsNode.isArray()) {
      return points;
    }
    for (JsonNode item : pointsNode) {
      String targetTimeRaw = textAt(item, "targetTime", null);
      if (targetTimeRaw == null) {
        continue;
      }
      OffsetDateTime targetTime = OffsetDateTime.parse(targetTimeRaw);
      double predicted = item.path("predPowerKw").asDouble(0.0);
      double lower = item.path("lowerBoundKw").asDouble(Math.max(0.0, predicted * 0.9));
      double upper = item.path("upperBoundKw").asDouble(predicted * 1.1);
      points.add(new ForecastPoint(targetTime, predicted, lower, upper));
    }
    return points;
  }

  private String encodePath(String value) {
    return value.replace(" ", "%20");
  }

  private String encodeQuery(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private void seed() {
    OffsetDateTime now = OffsetDateTime.now();
    modelsByPlant.put("plant-demo-001", new ArrayList<>(List.of(
        new ModelVersionSummary(
            "plant-demo-001",
            "model-demo-20260720",
            "xgboost",
            "feature-v1",
            "production",
            now.minusDays(1),
            "rmse=95.2,mae=60.3,mape=7.1"))));
  }
}

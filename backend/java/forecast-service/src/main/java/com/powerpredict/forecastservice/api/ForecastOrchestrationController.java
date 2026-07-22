package com.powerpredict.forecastservice.api;

import com.powerpredict.common.api.ApiResponse;
import com.powerpredict.forecastservice.domain.ForecastJob;
import com.powerpredict.forecastservice.domain.ModelVersionSummary;
import com.powerpredict.forecastservice.domain.TrainJob;
import com.powerpredict.forecastservice.service.ForecastOrchestrationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
public class ForecastOrchestrationController {
  private final ForecastOrchestrationService forecastOrchestrationService;

  public ForecastOrchestrationController(ForecastOrchestrationService forecastOrchestrationService) {
    this.forecastOrchestrationService = forecastOrchestrationService;
  }

  @PostMapping("/model-train-jobs")
  public ApiResponse<TrainJob> submitTrainJob(@RequestBody TrainJobRequest request) {
    TrainJob job = forecastOrchestrationService.submitTrainJob(new ForecastOrchestrationService.TrainJobCommand(
        request.plantId,
        request.startDate,
        request.endDate,
        request.featureSetVersion,
        request.algorithm));
    return ApiResponse.ok(job);
  }

  @GetMapping("/model-train-jobs/{jobId}")
  public ApiResponse<TrainJob> getTrainJob(@PathVariable("jobId") String jobId) {
    return ApiResponse.ok(forecastOrchestrationService.getTrainJob(jobId));
  }

  @GetMapping("/plants/{plantId}/models")
  public ApiResponse<List<ModelVersionSummary>> listModels(@PathVariable("plantId") String plantId) {
    return ApiResponse.ok(forecastOrchestrationService.listPlantModels(plantId));
  }

  @PutMapping("/plants/{plantId}/models/{modelVersion}:promote")
  public ApiResponse<ModelVersionSummary> promoteModel(
      @PathVariable("plantId") String plantId,
      @PathVariable("modelVersion") String modelVersion) {
    return ApiResponse.ok(forecastOrchestrationService.promoteModel(plantId, modelVersion));
  }

  @PostMapping("/jobs/dayahead")
  public ApiResponse<ForecastJob> submitForecastJob(@RequestBody ForecastJobRequest request) {
    ForecastJob job = forecastOrchestrationService.submitForecastJob(new ForecastOrchestrationService.ForecastJobCommand(
        request.plantId,
        request.forecastDate,
        request.modelVersion,
        request.weatherSource));
    return ApiResponse.ok(job);
  }

  @GetMapping("/jobs/{jobId}")
  public ApiResponse<ForecastJob> getForecastJob(@PathVariable("jobId") String jobId) {
    return ApiResponse.ok(forecastOrchestrationService.getForecastJob(jobId));
  }

  @GetMapping("/plants/{plantId}/dayahead")
  public ApiResponse<Map<String, Object>> listDayAhead(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "forecastDate", required = false) LocalDate forecastDate,
      @RequestParam(name = "modelVersion", required = false) String modelVersion) {
    List<ForecastJob> jobs = forecastOrchestrationService.listDayAhead(plantId, forecastDate, modelVersion);
    return ApiResponse.ok(Map.of("items", jobs, "total", jobs.size()));
  }

  public static class TrainJobRequest {
    public String plantId;
    public LocalDate startDate;
    public LocalDate endDate;
    public String featureSetVersion;
    public String algorithm;
  }

  public static class ForecastJobRequest {
    public String plantId;
    public LocalDate forecastDate;
    public String modelVersion;
    public String weatherSource;
  }
}

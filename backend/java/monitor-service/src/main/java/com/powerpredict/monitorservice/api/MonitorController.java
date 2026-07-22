package com.powerpredict.monitorservice.api;

import com.powerpredict.common.api.ApiResponse;
import com.powerpredict.monitorservice.domain.GenerationRecord;
import com.powerpredict.monitorservice.domain.ImportJob;
import com.powerpredict.monitorservice.domain.WeatherRecord;
import com.powerpredict.monitorservice.service.MonitorDomainService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
public class MonitorController {
  private final MonitorDomainService monitorDomainService;

  public MonitorController(MonitorDomainService monitorDomainService) {
    this.monitorDomainService = monitorDomainService;
  }

  @GetMapping("/plants/{plantId}/realtime")
  public ApiResponse<Map<String, Object>> realtime(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "at", required = false) OffsetDateTime at) {
    return ApiResponse.ok(monitorDomainService.realtime(plantId, at));
  }

  @GetMapping("/plants/{plantId}/timeseries")
  public ApiResponse<Map<String, Object>> timeseries(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "startTime", required = false) OffsetDateTime startTime,
      @RequestParam(name = "endTime", required = false) OffsetDateTime endTime,
      @RequestParam(name = "granularity", required = false) String granularity,
      @RequestParam(name = "metrics", required = false) List<String> metrics) {
    return ApiResponse.ok(monitorDomainService.timeseries(plantId, startTime, endTime, granularity, metrics));
  }

  @GetMapping("/plants/{plantId}/generation-records")
  public ApiResponse<Map<String, Object>> listGeneration(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "startTime", required = false) OffsetDateTime startTime,
      @RequestParam(name = "endTime", required = false) OffsetDateTime endTime) {
    List<GenerationRecord> all = monitorDomainService.listGenerationRecords(plantId, startTime, endTime);
    return ApiResponse.ok(page(all, pageNo, pageSize));
  }

  @PostMapping("/plants/{plantId}/generation-records")
  public ApiResponse<GenerationRecord> createGeneration(
      @PathVariable("plantId") String plantId,
      @RequestBody GenerationUpsertRequest request) {
    return ApiResponse.ok(monitorDomainService.createGenerationRecord(plantId, request.toCommand()));
  }

  @PutMapping("/plants/{plantId}/generation-records/{recordId}")
  public ApiResponse<GenerationRecord> updateGeneration(
      @PathVariable("plantId") String plantId,
      @PathVariable("recordId") String recordId,
      @RequestBody GenerationUpsertRequest request) {
    return ApiResponse.ok(monitorDomainService.updateGenerationRecord(plantId, recordId, request.toCommand()));
  }

  @DeleteMapping("/plants/{plantId}/generation-records/{recordId}")
  public ApiResponse<Map<String, Object>> deleteGeneration(
      @PathVariable("plantId") String plantId,
      @PathVariable("recordId") String recordId) {
    monitorDomainService.deleteGenerationRecord(plantId, recordId);
    return ApiResponse.ok(Map.of("deleted", true, "recordId", recordId));
  }

  @PostMapping("/plants/{plantId}/generation-records:import")
  public ApiResponse<ImportJob> importGeneration(
      @PathVariable("plantId") String plantId,
      @RequestParam("file") MultipartFile file) {
    return ApiResponse.ok(monitorDomainService.submitGenerationImport(plantId, file.getOriginalFilename()));
  }

  @GetMapping("/plants/{plantId}/weather-records")
  public ApiResponse<Map<String, Object>> listWeather(
      @PathVariable("plantId") String plantId,
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "startTime", required = false) OffsetDateTime startTime,
      @RequestParam(name = "endTime", required = false) OffsetDateTime endTime) {
    List<WeatherRecord> all = monitorDomainService.listWeatherRecords(plantId, startTime, endTime);
    return ApiResponse.ok(page(all, pageNo, pageSize));
  }

  @PostMapping("/plants/{plantId}/weather-records")
  public ApiResponse<WeatherRecord> createWeather(
      @PathVariable("plantId") String plantId,
      @RequestBody WeatherUpsertRequest request) {
    return ApiResponse.ok(monitorDomainService.createWeatherRecord(plantId, request.toCommand()));
  }

  @PutMapping("/plants/{plantId}/weather-records/{recordId}")
  public ApiResponse<WeatherRecord> updateWeather(
      @PathVariable("plantId") String plantId,
      @PathVariable("recordId") String recordId,
      @RequestBody WeatherUpsertRequest request) {
    return ApiResponse.ok(monitorDomainService.updateWeatherRecord(plantId, recordId, request.toCommand()));
  }

  @DeleteMapping("/plants/{plantId}/weather-records/{recordId}")
  public ApiResponse<Map<String, Object>> deleteWeather(
      @PathVariable("plantId") String plantId,
      @PathVariable("recordId") String recordId) {
    monitorDomainService.deleteWeatherRecord(plantId, recordId);
    return ApiResponse.ok(Map.of("deleted", true, "recordId", recordId));
  }

  @PostMapping("/plants/{plantId}/weather-records:import")
  public ApiResponse<ImportJob> importWeather(
      @PathVariable("plantId") String plantId,
      @RequestParam("file") MultipartFile file) {
    return ApiResponse.ok(monitorDomainService.submitWeatherImport(plantId, file.getOriginalFilename()));
  }

  @GetMapping("/import-jobs/{jobId}")
  public ApiResponse<ImportJob> getImportJob(@PathVariable("jobId") String jobId) {
    return ApiResponse.ok(monitorDomainService.getImportJob(jobId));
  }

  private Map<String, Object> page(List<?> all, int pageNo, int pageSize) {
    int from = Math.max(0, (pageNo - 1) * pageSize);
    int to = Math.min(all.size(), from + pageSize);
    List<?> items = from >= all.size() ? List.of() : all.subList(from, to);
    return Map.of("items", items, "pageNo", pageNo, "pageSize", pageSize, "total", all.size());
  }

  public static class GenerationUpsertRequest {
    public String deviceId;
    public OffsetDateTime recordTime;
    public Double activePowerKw;
    public Double reactivePowerKvar;
    public Double apparentPowerKva;
    public Double powerFactorTotal;
    public Double powerFactorPhaseA;
    public Double powerFactorPhaseB;
    public Double powerFactorPhaseC;
    public Double voltagePhaseA;
    public Double voltagePhaseB;
    public Double voltagePhaseC;
    public Double currentPhaseA;
    public Double currentPhaseB;
    public Double currentPhaseC;
    public Double frequencyHz;
    public Double energy15mKwh;
    public Double energyDailyKwh;
    public String deviceStatusCode;

    MonitorDomainService.GenerationUpsertCommand toCommand() {
      return new MonitorDomainService.GenerationUpsertCommand(
          deviceId,
          recordTime,
          activePowerKw,
          reactivePowerKvar,
          apparentPowerKva,
          powerFactorTotal,
          powerFactorPhaseA,
          powerFactorPhaseB,
          powerFactorPhaseC,
          voltagePhaseA,
          voltagePhaseB,
          voltagePhaseC,
          currentPhaseA,
          currentPhaseB,
          currentPhaseC,
          frequencyHz,
          energy15mKwh,
          energyDailyKwh,
          deviceStatusCode);
    }
  }

  public static class WeatherUpsertRequest {
    public OffsetDateTime recordTime;
    public Double ghi;
    public Double dni;
    public Double dhi;
    public Double temperatureC;
    public Double humidityPct;
    public Double cloudCoverPct;
    public Double windSpeedMs;
    public Double windDirectionDeg;

    MonitorDomainService.WeatherUpsertCommand toCommand() {
      return new MonitorDomainService.WeatherUpsertCommand(
          recordTime,
          ghi,
          dni,
          dhi,
          temperatureC,
          humidityPct,
          cloudCoverPct,
          windSpeedMs,
          windDirectionDeg);
    }
  }
}

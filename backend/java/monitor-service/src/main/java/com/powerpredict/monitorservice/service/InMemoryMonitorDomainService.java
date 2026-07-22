package com.powerpredict.monitorservice.service;

import com.powerpredict.monitorservice.domain.GenerationRecord;
import com.powerpredict.monitorservice.domain.ImportJob;
import com.powerpredict.monitorservice.domain.WeatherRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
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
public class InMemoryMonitorDomainService implements MonitorDomainService {
  private final Map<String, GenerationRecord> generationStore = new LinkedHashMap<>();
  private final Map<String, WeatherRecord> weatherStore = new LinkedHashMap<>();
  private final Map<String, ImportJob> importJobs = new LinkedHashMap<>();

  public InMemoryMonitorDomainService() {
    seedDemoData();
  }

  @Override
  public synchronized List<GenerationRecord> listGenerationRecords(String plantId, OffsetDateTime startTime, OffsetDateTime endTime) {
    return filterGeneration(plantId, startTime, endTime);
  }

  @Override
  public synchronized GenerationRecord createGenerationRecord(String plantId, GenerationUpsertCommand command) {
    validatePowerFactor(command.powerFactorTotal());
    String id = "gen-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    OffsetDateTime now = OffsetDateTime.now();
    GenerationRecord record = new GenerationRecord(
        id,
        plantId,
        command.deviceId(),
        fallbackTime(command.recordTime(), now),
        command.activePowerKw(),
        command.reactivePowerKvar(),
        command.apparentPowerKva(),
        command.powerFactorTotal(),
        command.powerFactorPhaseA(),
        command.powerFactorPhaseB(),
        command.powerFactorPhaseC(),
        command.voltagePhaseA(),
        command.voltagePhaseB(),
        command.voltagePhaseC(),
        command.currentPhaseA(),
        command.currentPhaseB(),
        command.currentPhaseC(),
        command.frequencyHz(),
        command.energy15mKwh(),
        command.energyDailyKwh(),
        command.deviceStatusCode(),
        "MANUAL",
        "api-user",
        now,
        now);
    generationStore.put(id, record);
    return record;
  }

  @Override
  public synchronized GenerationRecord updateGenerationRecord(String plantId, String recordId, GenerationUpsertCommand command) {
    GenerationRecord old = getGeneration(recordId);
    if (!old.plantId().equals(plantId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not in target plant");
    }
    validatePowerFactor(command.powerFactorTotal());
    GenerationRecord updated = new GenerationRecord(
        old.id(),
        old.plantId(),
        command.deviceId() == null ? old.deviceId() : command.deviceId(),
        command.recordTime() == null ? old.recordTime() : command.recordTime(),
        command.activePowerKw() == null ? old.activePowerKw() : command.activePowerKw(),
        command.reactivePowerKvar() == null ? old.reactivePowerKvar() : command.reactivePowerKvar(),
        command.apparentPowerKva() == null ? old.apparentPowerKva() : command.apparentPowerKva(),
        command.powerFactorTotal() == null ? old.powerFactorTotal() : command.powerFactorTotal(),
        command.powerFactorPhaseA() == null ? old.powerFactorPhaseA() : command.powerFactorPhaseA(),
        command.powerFactorPhaseB() == null ? old.powerFactorPhaseB() : command.powerFactorPhaseB(),
        command.powerFactorPhaseC() == null ? old.powerFactorPhaseC() : command.powerFactorPhaseC(),
        command.voltagePhaseA() == null ? old.voltagePhaseA() : command.voltagePhaseA(),
        command.voltagePhaseB() == null ? old.voltagePhaseB() : command.voltagePhaseB(),
        command.voltagePhaseC() == null ? old.voltagePhaseC() : command.voltagePhaseC(),
        command.currentPhaseA() == null ? old.currentPhaseA() : command.currentPhaseA(),
        command.currentPhaseB() == null ? old.currentPhaseB() : command.currentPhaseB(),
        command.currentPhaseC() == null ? old.currentPhaseC() : command.currentPhaseC(),
        command.frequencyHz() == null ? old.frequencyHz() : command.frequencyHz(),
        command.energy15mKwh() == null ? old.energy15mKwh() : command.energy15mKwh(),
        command.energyDailyKwh() == null ? old.energyDailyKwh() : command.energyDailyKwh(),
        command.deviceStatusCode() == null ? old.deviceStatusCode() : command.deviceStatusCode(),
        old.source(),
        old.createdBy(),
        old.createdAt(),
        OffsetDateTime.now());
    generationStore.put(recordId, updated);
    return updated;
  }

  @Override
  public synchronized void deleteGenerationRecord(String plantId, String recordId) {
    GenerationRecord old = getGeneration(recordId);
    if (!old.plantId().equals(plantId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not in target plant");
    }
    generationStore.remove(recordId);
  }

  @Override
  public synchronized List<WeatherRecord> listWeatherRecords(String plantId, OffsetDateTime startTime, OffsetDateTime endTime) {
    List<WeatherRecord> items = new ArrayList<>();
    for (WeatherRecord record : weatherStore.values()) {
      if (!record.plantId().equals(plantId)) {
        continue;
      }
      if (startTime != null && record.recordTime().isBefore(startTime)) {
        continue;
      }
      if (endTime != null && record.recordTime().isAfter(endTime)) {
        continue;
      }
      items.add(record);
    }
    items.sort(Comparator.comparing(WeatherRecord::recordTime));
    return items;
  }

  @Override
  public synchronized WeatherRecord createWeatherRecord(String plantId, WeatherUpsertCommand command) {
    String id = "weather-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    OffsetDateTime now = OffsetDateTime.now();
    WeatherRecord record = new WeatherRecord(
        id,
        plantId,
        fallbackTime(command.recordTime(), now),
        command.ghi(),
        command.dni(),
        command.dhi(),
        command.temperatureC(),
        command.humidityPct(),
        command.cloudCoverPct(),
        command.windSpeedMs(),
        command.windDirectionDeg(),
        "MANUAL",
        "api-user",
        now,
        now);
    weatherStore.put(id, record);
    return record;
  }

  @Override
  public synchronized WeatherRecord updateWeatherRecord(String plantId, String recordId, WeatherUpsertCommand command) {
    WeatherRecord old = getWeather(recordId);
    if (!old.plantId().equals(plantId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not in target plant");
    }
    WeatherRecord updated = new WeatherRecord(
        old.id(),
        old.plantId(),
        command.recordTime() == null ? old.recordTime() : command.recordTime(),
        command.ghi() == null ? old.ghi() : command.ghi(),
        command.dni() == null ? old.dni() : command.dni(),
        command.dhi() == null ? old.dhi() : command.dhi(),
        command.temperatureC() == null ? old.temperatureC() : command.temperatureC(),
        command.humidityPct() == null ? old.humidityPct() : command.humidityPct(),
        command.cloudCoverPct() == null ? old.cloudCoverPct() : command.cloudCoverPct(),
        command.windSpeedMs() == null ? old.windSpeedMs() : command.windSpeedMs(),
        command.windDirectionDeg() == null ? old.windDirectionDeg() : command.windDirectionDeg(),
        old.source(),
        old.createdBy(),
        old.createdAt(),
        OffsetDateTime.now());
    weatherStore.put(recordId, updated);
    return updated;
  }

  @Override
  public synchronized void deleteWeatherRecord(String plantId, String recordId) {
    WeatherRecord old = getWeather(recordId);
    if (!old.plantId().equals(plantId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not in target plant");
    }
    weatherStore.remove(recordId);
  }

  @Override
  public synchronized ImportJob submitGenerationImport(String plantId, String fileName) {
    return createImportJob("GENERATION_IMPORT", plantId, fileName);
  }

  @Override
  public synchronized ImportJob submitWeatherImport(String plantId, String fileName) {
    return createImportJob("WEATHER_IMPORT", plantId, fileName);
  }

  @Override
  public synchronized ImportJob getImportJob(String jobId) {
    ImportJob job = importJobs.get(jobId);
    if (job == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import job not found: " + jobId);
    }
    return job;
  }

  @Override
  public synchronized Map<String, Object> realtime(String plantId, OffsetDateTime at) {
    OffsetDateTime point = at == null ? OffsetDateTime.now() : at;
    GenerationRecord latest = filterGeneration(plantId, point.minusMinutes(30), point.plusMinutes(30)).stream()
        .max(Comparator.comparing(GenerationRecord::recordTime))
        .orElse(null);

    if (latest == null) {
      return Map.of(
          "plantId", plantId,
          "recordTime", point,
          "activePowerKw", 0.0,
          "reactivePowerKvar", 0.0,
          "powerFactorTotal", 0.0,
          "energy15mKwh", 0.0,
          "energyDailyKwh", 0.0);
    }

    return Map.ofEntries(
      Map.entry("plantId", latest.plantId()),
      Map.entry("recordTime", latest.recordTime()),
      Map.entry("activePowerKw", value(latest.activePowerKw())),
      Map.entry("reactivePowerKvar", value(latest.reactivePowerKvar())),
      Map.entry("powerFactorTotal", value(latest.powerFactorTotal())),
      Map.entry("voltagePhaseA", value(latest.voltagePhaseA())),
      Map.entry("voltagePhaseB", value(latest.voltagePhaseB())),
      Map.entry("voltagePhaseC", value(latest.voltagePhaseC())),
      Map.entry("currentPhaseA", value(latest.currentPhaseA())),
      Map.entry("currentPhaseB", value(latest.currentPhaseB())),
      Map.entry("currentPhaseC", value(latest.currentPhaseC())),
      Map.entry("energy15mKwh", value(latest.energy15mKwh())),
      Map.entry("energyDailyKwh", value(latest.energyDailyKwh())));
  }

  @Override
  public synchronized Map<String, Object> timeseries(
      String plantId,
      OffsetDateTime startTime,
      OffsetDateTime endTime,
      String granularity,
      List<String> metrics) {
    List<GenerationRecord> records = filterGeneration(plantId, startTime, endTime);
    List<Map<String, Object>> points = new ArrayList<>();
    for (GenerationRecord r : records) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("recordTime", r.recordTime());
      row.put("activePowerKw", value(r.activePowerKw()));
      row.put("reactivePowerKvar", value(r.reactivePowerKvar()));
      row.put("powerFactorTotal", value(r.powerFactorTotal()));
      row.put("energy15mKwh", value(r.energy15mKwh()));
      row.put("energyDailyKwh", value(r.energyDailyKwh()));
      points.add(row);
    }
    return Map.of(
        "plantId", plantId,
        "granularity", granularity == null || granularity.isBlank() ? "PT15M" : granularity,
        "metrics", metrics == null ? List.of() : metrics,
        "points", points);
  }

  private ImportJob createImportJob(String type, String plantId, String fileName) {
    String id = "import-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    OffsetDateTime now = OffsetDateTime.now();
    ImportJob job = new ImportJob(
        id,
        type,
        plantId,
        "SUCCESS",
        fileName,
        0,
        0,
        "",
        now,
        now.plusSeconds(1));
    importJobs.put(id, job);
    return job;
  }

  private List<GenerationRecord> filterGeneration(String plantId, OffsetDateTime startTime, OffsetDateTime endTime) {
    List<GenerationRecord> items = new ArrayList<>();
    for (GenerationRecord record : generationStore.values()) {
      if (!record.plantId().equals(plantId)) {
        continue;
      }
      if (startTime != null && record.recordTime().isBefore(startTime)) {
        continue;
      }
      if (endTime != null && record.recordTime().isAfter(endTime)) {
        continue;
      }
      items.add(record);
    }
    items.sort(Comparator.comparing(GenerationRecord::recordTime));
    return items;
  }

  private GenerationRecord getGeneration(String recordId) {
    GenerationRecord record = generationStore.get(recordId);
    if (record == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Generation record not found: " + recordId);
    }
    return record;
  }

  private WeatherRecord getWeather(String recordId) {
    WeatherRecord record = weatherStore.get(recordId);
    if (record == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Weather record not found: " + recordId);
    }
    return record;
  }

  private OffsetDateTime fallbackTime(OffsetDateTime value, OffsetDateTime fallback) {
    return value == null ? fallback : value;
  }

  private void validatePowerFactor(Double value) {
    if (value == null) {
      return;
    }
    if (value < -1.0 || value > 1.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "powerFactorTotal out of range [-1,1]");
    }
  }

  private double value(Double value) {
    return value == null ? 0.0 : value;
  }

  private void seedDemoData() {
    OffsetDateTime now = OffsetDateTime.now().withMinute(0).withSecond(0).withNano(0);
    for (int i = 0; i < 8; i++) {
      OffsetDateTime ts = now.minus(Duration.ofMinutes((long) (7 - i) * 15));
      String id = "seed-gen-" + i;
      GenerationRecord record = new GenerationRecord(
          id,
          "plant-demo-001",
          "inv-001",
          ts,
          1200.0 + i * 15,
          80.0 + i,
          1202.0 + i * 15,
          0.98,
          0.98,
          0.98,
          0.97,
          380.0,
          381.0,
          379.5,
          95.0,
          94.2,
          95.7,
          50.0,
          285.0 + i,
          5200.0 + i * 285,
          "ONLINE",
          "SCADA",
          "seed",
          ts,
          ts);
      generationStore.put(id, record);
    }

    String weatherId = "seed-weather-1";
    WeatherRecord weather = new WeatherRecord(
        weatherId,
        "plant-demo-001",
        now,
        620.0,
        410.0,
        210.0,
        30.5,
        62.0,
        35.0,
        4.5,
        140.0,
        "API",
        "seed",
        now,
        now);
    weatherStore.put(weatherId, weather);
  }
}

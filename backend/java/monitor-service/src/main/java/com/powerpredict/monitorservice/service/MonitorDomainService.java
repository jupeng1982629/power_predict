package com.powerpredict.monitorservice.service;

import com.powerpredict.monitorservice.domain.GenerationRecord;
import com.powerpredict.monitorservice.domain.ImportJob;
import com.powerpredict.monitorservice.domain.WeatherRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface MonitorDomainService {
  List<GenerationRecord> listGenerationRecords(String plantId, OffsetDateTime startTime, OffsetDateTime endTime);

  GenerationRecord createGenerationRecord(String plantId, GenerationUpsertCommand command);

  GenerationRecord updateGenerationRecord(String plantId, String recordId, GenerationUpsertCommand command);

  void deleteGenerationRecord(String plantId, String recordId);

  List<WeatherRecord> listWeatherRecords(String plantId, OffsetDateTime startTime, OffsetDateTime endTime);

  WeatherRecord createWeatherRecord(String plantId, WeatherUpsertCommand command);

  WeatherRecord updateWeatherRecord(String plantId, String recordId, WeatherUpsertCommand command);

  void deleteWeatherRecord(String plantId, String recordId);

  ImportJob submitGenerationImport(String plantId, String fileName);

  ImportJob submitWeatherImport(String plantId, String fileName);

  ImportJob getImportJob(String jobId);

  Map<String, Object> realtime(String plantId, OffsetDateTime at);

  Map<String, Object> timeseries(String plantId, OffsetDateTime startTime, OffsetDateTime endTime, String granularity, List<String> metrics);

  record GenerationUpsertCommand(
      String deviceId,
      OffsetDateTime recordTime,
      Double activePowerKw,
      Double reactivePowerKvar,
      Double apparentPowerKva,
      Double powerFactorTotal,
      Double powerFactorPhaseA,
      Double powerFactorPhaseB,
      Double powerFactorPhaseC,
      Double voltagePhaseA,
      Double voltagePhaseB,
      Double voltagePhaseC,
      Double currentPhaseA,
      Double currentPhaseB,
      Double currentPhaseC,
      Double frequencyHz,
      Double energy15mKwh,
      Double energyDailyKwh,
      String deviceStatusCode
  ) {
  }

  record WeatherUpsertCommand(
      OffsetDateTime recordTime,
      Double ghi,
      Double dni,
      Double dhi,
      Double temperatureC,
      Double humidityPct,
      Double cloudCoverPct,
      Double windSpeedMs,
      Double windDirectionDeg
  ) {
  }
}

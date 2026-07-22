package com.powerpredict.forecastservice.service;

import com.powerpredict.forecastservice.domain.ForecastJob;
import com.powerpredict.forecastservice.domain.ModelVersionSummary;
import com.powerpredict.forecastservice.domain.TrainJob;
import java.time.LocalDate;
import java.util.List;

public interface ForecastOrchestrationService {
  TrainJob submitTrainJob(TrainJobCommand command);

  TrainJob getTrainJob(String jobId);

  List<ModelVersionSummary> listPlantModels(String plantId);

  ModelVersionSummary promoteModel(String plantId, String modelVersion);

  ForecastJob submitForecastJob(ForecastJobCommand command);

  ForecastJob getForecastJob(String jobId);

  List<ForecastJob> listDayAhead(String plantId, LocalDate forecastDate, String modelVersion);

  record TrainJobCommand(
      String plantId,
      LocalDate startDate,
      LocalDate endDate,
      String featureSetVersion,
      String algorithm
  ) {
  }

  record ForecastJobCommand(
      String plantId,
      LocalDate forecastDate,
      String modelVersion,
      String weatherSource
  ) {
  }
}

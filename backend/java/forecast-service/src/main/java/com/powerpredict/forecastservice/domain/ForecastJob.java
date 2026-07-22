package com.powerpredict.forecastservice.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ForecastJob(
    String jobId,
    String plantId,
    LocalDate forecastDate,
    String modelVersion,
    String weatherSource,
    String status,
    List<ForecastPoint> points,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

package com.powerpredict.forecastservice.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TrainJob(
    String jobId,
    String plantId,
    LocalDate startDate,
    LocalDate endDate,
    String algorithm,
    String featureSetVersion,
    String status,
    String modelVersion,
    String message,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

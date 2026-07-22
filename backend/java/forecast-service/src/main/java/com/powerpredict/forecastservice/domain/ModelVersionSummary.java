package com.powerpredict.forecastservice.domain;

import java.time.OffsetDateTime;

public record ModelVersionSummary(
    String plantId,
    String modelVersion,
    String algorithm,
    String featureVersion,
    String status,
    OffsetDateTime trainedAt,
    String metricsSummary
) {
}

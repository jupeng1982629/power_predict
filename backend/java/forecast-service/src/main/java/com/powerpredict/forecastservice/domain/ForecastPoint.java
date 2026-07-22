package com.powerpredict.forecastservice.domain;

import java.time.OffsetDateTime;

public record ForecastPoint(
    OffsetDateTime targetTime,
    double predictedPowerKw,
    double lowerBoundKw,
    double upperBoundKw
) {
}

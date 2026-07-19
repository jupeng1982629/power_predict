package com.powerpredict.common.demo;

import java.time.OffsetDateTime;

public record DemoSummary(
    String plantName,
    int plantCount,
    int activePlantCount,
    int forecastJobCount,
    String latestForecastDate,
    OffsetDateTime generatedAt
) {
}
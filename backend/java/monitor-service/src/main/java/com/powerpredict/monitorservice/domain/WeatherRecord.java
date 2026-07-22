package com.powerpredict.monitorservice.domain;

import java.time.OffsetDateTime;

public record WeatherRecord(
    String id,
    String plantId,
    OffsetDateTime recordTime,
    Double ghi,
    Double dni,
    Double dhi,
    Double temperatureC,
    Double humidityPct,
    Double cloudCoverPct,
    Double windSpeedMs,
    Double windDirectionDeg,
    String source,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

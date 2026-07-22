package com.powerpredict.plantservice.domain;

import java.time.OffsetDateTime;

public record PlantProfile(
    String plantId,
    String plantName,
        double capacityMw,
    double latitude,
    double longitude,
        Double elevationM,
        Double tiltAngle,
        Double azimuthAngle,
    String timezone,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public String getPlantCode() {
        return plantId;
    }

    public double getCapacityKw() {
        return capacityMw * 1000.0;
    }
}

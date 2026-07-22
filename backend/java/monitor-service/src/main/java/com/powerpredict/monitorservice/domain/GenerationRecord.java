package com.powerpredict.monitorservice.domain;

import java.time.OffsetDateTime;

public record GenerationRecord(
    String id,
    String plantId,
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
    String deviceStatusCode,
    String source,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

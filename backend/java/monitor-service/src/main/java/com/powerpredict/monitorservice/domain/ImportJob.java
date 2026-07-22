package com.powerpredict.monitorservice.domain;

import java.time.OffsetDateTime;

public record ImportJob(
    String id,
    String jobType,
    String plantId,
    String status,
    String sourceFileName,
    long successCount,
    long failedCount,
    String errorReportUri,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt
) {
}

package com.flowora.erp.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class ExportDtos {
    private ExportDtos() { }

    public record ExportCreate(@NotBlank @Size(max = 64) String resourceType,
                               Map<String, Object> filters,
                               @Pattern(regexp = "[A-Za-z]{2}(-[A-Za-z]{2})?") String locale) { }

    public record ExportJobView(String id, String resourceType, String format, String status,
                                long rowCount, String resultFilename, String errorCode,
                                Instant expiresAt, Instant createdAt, Instant completedAt) { }

    public record ExportDownload(java.nio.file.Path path, String filename, String sha256Hex) { }
}

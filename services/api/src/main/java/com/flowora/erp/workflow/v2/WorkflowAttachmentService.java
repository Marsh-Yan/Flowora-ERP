package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.AttachmentView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkflowAttachmentService {
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "com", "bat", "cmd", "ps1", "sh", "js", "jar", "msi", "scr", "vbs", "html", "svg"
    );

    private final JdbcTemplate jdbcTemplate;
    private final WorkflowResourceAccessPolicy accessPolicy;
    private final Path root;
    private final long maxBytes;
    private final Set<String> allowedTypes;

    public WorkflowAttachmentService(
            JdbcTemplate jdbcTemplate,
            WorkflowResourceAccessPolicy accessPolicy,
            @Value("${flowora.attachment.root}") String root,
            @Value("${flowora.attachment.max-bytes}") long maxBytes,
            @Value("${flowora.attachment.allowed-types}") String allowedTypes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessPolicy = accessPolicy;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.allowedTypes = Arrays.stream(allowedTypes.split(","))
                .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public AttachmentView upload(
            FloworaPrincipal principal,
            String resourceType,
            String resourceId,
            MultipartFile file
    ) {
        String normalized = accessPolicy.require(principal, resourceType, resourceId, "upload");
        validate(file);
        String id = UUID.randomUUID().toString();
        String filename = safeFilename(file.getOriginalFilename());
        String mediaType = file.getContentType().toLowerCase(Locale.ROOT);
        String storageKey = principal.organizationId() + "/" + id;
        Path finalPath = resolve(storageKey);
        Path stagingPath = resolve(".staging/" + id + ".part");
        try {
            Files.createDirectories(stagingPath.getParent());
            Files.createDirectories(finalPath.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, stagingPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(stagingPath) != file.getSize()) {
                throw invalid("ATTACHMENT_SIZE_MISMATCH");
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            jdbcTemplate.update("""
                    INSERT INTO flowora_attachment (
                        id, organization_id, resource_type, resource_id, original_filename,
                        media_type, size_bytes, sha256_hex, storage_key, uploader_user_id, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'STAGED')
                    """, id, principal.organizationId(), normalized, resourceId, filename,
                    mediaType, file.getSize(), sha256, storageKey, principal.userId());
            Files.move(stagingPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            jdbcTemplate.update("""
                    UPDATE flowora_attachment SET status = 'LINKED', linked_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND organization_id = ? AND status = 'STAGED'
                    """, id, principal.organizationId());
            cleanupOnRollback(finalPath, stagingPath);
            return get(principal, id);
        } catch (PlatformApiException exception) {
            deleteQuietly(stagingPath);
            deleteQuietly(finalPath);
            throw exception;
        } catch (Exception exception) {
            deleteQuietly(stagingPath);
            deleteQuietly(finalPath);
            throw new PlatformApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "ATTACHMENT_STORAGE_FAILED", "errors.internal");
        }
    }

    public List<AttachmentView> list(FloworaPrincipal principal, String resourceType, String resourceId) {
        String normalized = accessPolicy.require(principal, resourceType, resourceId, "attachment-view");
        return jdbcTemplate.query("""
                SELECT id, resource_type, resource_id, original_filename, media_type, size_bytes,
                       sha256_hex, uploader_user_id, status, created_at, linked_at
                FROM flowora_attachment
                WHERE organization_id = ? AND resource_type = ? AND resource_id = ?
                  AND status = 'LINKED' AND deleted_at IS NULL
                ORDER BY created_at, id
                """, (rs, rowNum) -> view(rs), principal.organizationId(), normalized, resourceId);
    }

    public Download getDownload(FloworaPrincipal principal, String attachmentId) {
        AttachmentRow row = jdbcTemplate.query("""
                SELECT id, resource_type, resource_id, original_filename, media_type, size_bytes, storage_key
                FROM flowora_attachment
                WHERE id = ? AND organization_id = ? AND status = 'LINKED' AND deleted_at IS NULL
                """, rs -> rs.next() ? new AttachmentRow(
                rs.getString("id"), rs.getString("resource_type"), rs.getString("resource_id"),
                rs.getString("original_filename"), rs.getString("media_type"),
                rs.getLong("size_bytes"), rs.getString("storage_key")) : null,
                attachmentId, principal.organizationId());
        if (row == null) notFound();
        accessPolicy.require(principal, row.resourceType(), row.resourceId(), "attachment-view");
        Path path = resolve(row.storageKey());
        if (!Files.isRegularFile(path)) notFound();
        return new Download(new FileSystemResource(path), row.filename(), row.mediaType(), row.sizeBytes());
    }

    private AttachmentView get(FloworaPrincipal principal, String id) {
        return jdbcTemplate.query("""
                SELECT id, resource_type, resource_id, original_filename, media_type, size_bytes,
                       sha256_hex, uploader_user_id, status, created_at, linked_at
                FROM flowora_attachment WHERE id = ? AND organization_id = ?
                """, rs -> {
            if (!rs.next()) {
                notFound();
            }
            return view(rs);
        }, id, principal.organizationId());
    }

    private AttachmentView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AttachmentView(
                rs.getString("id"), rs.getString("resource_type"), rs.getString("resource_id"),
                rs.getString("original_filename"), rs.getString("media_type"), rs.getLong("size_bytes"),
                rs.getString("sha256_hex"), rs.getString("uploader_user_id"), rs.getString("status"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("linked_at")));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) throw invalid("ATTACHMENT_EMPTY");
        if (file.getSize() > maxBytes) throw invalid("ATTACHMENT_TOO_LARGE");
        String type = file.getContentType();
        if (type == null || !allowedTypes.contains(type.toLowerCase(Locale.ROOT))) {
            throw invalid("ATTACHMENT_TYPE_NOT_ALLOWED");
        }
        String name = safeFilename(file.getOriginalFilename());
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (DANGEROUS_EXTENSIONS.contains(extension)) throw invalid("ATTACHMENT_TYPE_NOT_ALLOWED");
    }

    private String safeFilename(String original) {
        String value = original == null ? "attachment" : Path.of(original).getFileName().toString();
        value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.isEmpty()) value = "attachment";
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) throw invalid("ATTACHMENT_PATH_INVALID");
        return resolved;
    }

    private void cleanupOnRollback(Path... paths) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) Arrays.stream(paths).forEach(WorkflowAttachmentService.this::deleteQuietly);
            }
        });
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private PlatformApiException invalid(String code) {
        return new PlatformApiException(HttpStatus.BAD_REQUEST, code, "errors.validation");
    }

    private void notFound() {
        throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound");
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record Download(FileSystemResource resource, String filename, String mediaType, long sizeBytes) {}
    private record AttachmentRow(
            String id, String resourceType, String resourceId, String filename,
            String mediaType, long sizeBytes, String storageKey
    ) {}
}

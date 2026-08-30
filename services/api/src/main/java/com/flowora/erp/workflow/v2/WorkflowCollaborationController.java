package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.v2.WorkflowAttachmentService.Download;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.ActivityView;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.AttachmentView;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.CommentRequest;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.CommentView;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.MarkReadRequest;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.NotificationView;
import com.flowora.erp.workflow.v2.WorkflowCollaborationDtos.UnreadCount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/collaboration")
@Profile("local")
public class WorkflowCollaborationController {
    private final WorkflowCollaborationService collaborationService;
    private final WorkflowAttachmentService attachmentService;
    private final FloworaAuthorization authorization;

    public WorkflowCollaborationController(
            WorkflowCollaborationService collaborationService,
            WorkflowAttachmentService attachmentService,
            FloworaAuthorization authorization
    ) {
        this.collaborationService = collaborationService;
        this.attachmentService = attachmentService;
        this.authorization = authorization;
    }

    @GetMapping("/resources/{resourceType}/{resourceId}/comments")
    public ApiResponse<List<CommentView>> comments(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(collaborationService.comments(
                principal(authentication), resourceType, resourceId), request);
    }

    @PostMapping("/resources/{resourceType}/{resourceId}/comments")
    public ApiResponse<CommentView> addComment(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @Valid @RequestBody CommentRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(collaborationService.addComment(
                principal(authentication), resourceType, resourceId, body), request);
    }

    @GetMapping("/resources/{resourceType}/{resourceId}/activities")
    public ApiResponse<List<ActivityView>> activities(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(collaborationService.activities(
                principal(authentication), resourceType, resourceId), request);
    }

    @GetMapping("/resources/{resourceType}/{resourceId}/attachments")
    public ApiResponse<List<AttachmentView>> attachments(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(attachmentService.list(
                principal(authentication), resourceType, resourceId), request);
    }

    @PostMapping(value = "/resources/{resourceType}/{resourceId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentView> upload(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(attachmentService.upload(
                principal(authentication), resourceType, resourceId, file), request);
    }

    @GetMapping("/attachments/{attachmentId}/content")
    public ResponseEntity<FileSystemResource> download(
            @PathVariable String attachmentId,
            Authentication authentication
    ) {
        Download download = attachmentService.getDownload(principal(authentication), attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    @GetMapping("/notifications")
    public ApiResponse<List<NotificationView>> notifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(collaborationService.notifications(principal(authentication), unreadOnly), request);
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<UnreadCount> unreadCount(
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(new UnreadCount(collaborationService.unreadCount(principal(authentication))), request);
    }

    @PostMapping("/notifications/read")
    public ApiResponse<Map<String, Integer>> markRead(
            @Valid @RequestBody MarkReadRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(Map.of("updated", collaborationService.markRead(
                principal(authentication), body.notificationIds())), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return authorization.principal(authentication);
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}

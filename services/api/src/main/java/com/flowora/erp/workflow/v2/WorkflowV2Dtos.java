package com.flowora.erp.workflow.v2;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WorkflowV2Dtos {
    private WorkflowV2Dtos() {
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 64) String resourceType,
            @NotNull @Min(1) @Max(10000) Integer priority,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record ConditionGroup(
            @NotNull Logic logic,
            @NotNull List<@Valid Condition> conditions
    ) {
        public static ConditionGroup always() {
            return new ConditionGroup(Logic.ALL, List.of());
        }
    }

    public record Condition(
            @NotBlank String field,
            @NotNull Operator operator,
            @NotNull Object value
    ) {
    }

    public record StepRequest(
            @NotBlank @Size(max = 64) String stepKey,
            @NotBlank @Size(max = 160) String name,
            @NotNull @Min(1) Integer sequence,
            @NotNull CompletionMode completionMode,
            @NotNull ApproverType approverType,
            @Size(max = 96) String approverRef,
            @Min(1) @Max(2160) Integer dueHours
    ) {
    }

    public record VersionRequest(
            @NotNull @Valid ConditionGroup condition,
            @NotEmpty List<@Valid StepRequest> steps,
            boolean allowSelfApproval
    ) {
    }

    public record StartRequest(
            @NotBlank @Size(max = 64) String resourceType,
            @NotBlank @Size(max = 64) String resourceId,
            @NotNull @Min(1) Integer resourceRevision,
            @Size(max = 36) String ownerUserId,
            @NotNull BigDecimal amount,
            @NotBlank @Size(max = 3) String currency,
            @NotNull Map<String, Object> fields
    ) {
    }

    public record ActionRequest(
            @NotNull WorkflowAction action,
            @NotBlank @Size(max = 1000) String comment,
            @Size(max = 36) String transferToUserId
    ) {
    }

    public record DelegationRequest(
            @NotBlank @Size(max = 36) String delegateUserId,
            @Size(max = 64) String resourceType,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record TemplateView(
            String id,
            String code,
            String name,
            String resourceType,
            int priority,
            TemplateStatus status,
            String currentVersionId,
            long version
    ) {
    }

    public record VersionView(
            String id,
            String templateId,
            int versionNumber,
            VersionStatus status,
            ConditionGroup condition,
            List<StepRequest> steps,
            boolean allowSelfApproval,
            Instant publishedAt
    ) {
    }

    public record InstanceView(
            String id,
            String templateId,
            String workflowVersionId,
            String resourceType,
            String resourceId,
            int resourceRevision,
            String requesterUserId,
            String ownerUserId,
            InstanceStatus status,
            Integer currentSequence,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    public record TaskView(
            String id,
            String workflowInstanceId,
            String stepInstanceId,
            String resourceType,
            String resourceId,
            String stepName,
            String requesterUserId,
            String originalApproverUserId,
            String assigneeUserId,
            TaskStatus status,
            Instant dueAt,
            Instant completedAt,
            long version
    ) {
    }

    public record DecisionView(
            String id,
            String action,
            String originalApproverUserId,
            String actualActorUserId,
            String comment,
            Instant createdAt
    ) {
    }

    public enum Logic { ALL, ANY }
    public enum Operator { EQ, NE, GT, GTE, LT, LTE, IN, CONTAINS }
    public enum CompletionMode { SERIAL, PARALLEL_ALL, PARALLEL_ANY }
    public enum ApproverType { USER, ROLE, DEPARTMENT_MANAGER, DOCUMENT_OWNER, REQUESTER_MANAGER }
    public enum TemplateStatus { DRAFT, PUBLISHED, RETIRED }
    public enum VersionStatus { DRAFT, PUBLISHED, RETIRED }
    public enum InstanceStatus { RUNNING, APPROVED, REJECTED, RETURNED, WITHDRAWN, TERMINATED }
    public enum TaskStatus { OPEN, APPROVED, REJECTED, RETURNED, TRANSFERRED, CANCELLED }
    public enum WorkflowAction { APPROVE, REJECT, RETURN, WITHDRAW, TRANSFER, TERMINATE }
}

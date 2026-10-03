package com.flowora.erp.workflow.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.workflow.v2.WorkflowApproverResolver.ResolvedApprover;
import com.flowora.erp.workflow.v2.WorkflowTemplateService.MatchedVersion;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.ActionRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.CompletionMode;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.DecisionView;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.InstanceStatus;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.InstanceView;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.StartRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.StepRequest;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.TaskStatus;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.TaskView;
import com.flowora.erp.workflow.v2.WorkflowV2Dtos.WorkflowAction;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("local | production")
public class WorkflowEngineService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowResourceProjectionService projectionService;
    private final WorkflowTemplateService templateService;
    private final WorkflowApproverResolver approverResolver;
    private final WorkflowOutboxService outboxService;

    public WorkflowEngineService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            WorkflowTemplateService templateService,
            WorkflowApproverResolver approverResolver,
            WorkflowOutboxService outboxService,
            WorkflowResourceProjectionService projectionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.templateService = templateService;
        this.approverResolver = approverResolver;
        this.projectionService = projectionService;
        this.outboxService = outboxService;
    }

    // A missing template is detected before any engine write; all other failures still roll back.
    @Transactional(noRollbackFor = WorkflowTemplateNotFoundException.class)
    public InstanceView start(FloworaPrincipal actor, StartRequest request, String requestId) {
        Map<String, Object> context = new HashMap<>(request.fields());
        context.put("amount", request.amount());
        context.put("currency", normalize(request.currency()));
        context.put("resourceType", normalize(request.resourceType()));
        context.put("ownerUserId", nullToEmpty(request.ownerUserId()));
        MatchedVersion matched = templateService.match(actor.organizationId(), request.resourceType(), context);
        String instanceId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO flowora_workflow_instance (
                    id, organization_id, template_id, workflow_version_id, resource_type,
                    resource_id, resource_revision, requester_user_id, owner_user_id,
                    status, current_sequence_no, snapshot_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', 1, ?)
                """, instanceId, actor.organizationId(), matched.templateId(), matched.versionId(),
                normalize(request.resourceType()), request.resourceId().trim(), request.resourceRevision(),
                actor.userId(), blankToNull(request.ownerUserId()), json(Map.of(
                        "condition", matched.condition(),
                        "steps", matched.steps(),
                        "allowSelfApproval", matched.allowSelfApproval(),
                        "context", context
                )));
        activate(instanceId, actor.organizationId(), actor.userId(), request.ownerUserId(), context, matched, 1);
        activity(actor.organizationId(), request.resourceType(), request.resourceId(), actor.userId(),
                "WORKFLOW_SUBMITTED", "Workflow submitted", requestId);
        outboxService.enqueue(actor.organizationId(), "WORKFLOW_SUBMITTED", normalize(request.resourceType()),
                request.resourceId(), actor.userId(), Map.of("title", "Workflow submitted", "message", request.resourceId()));
        return instance(actor.organizationId(), instanceId);
    }

    @Transactional(readOnly = true)
    public List<TaskView> inbox(FloworaPrincipal actor, String view, boolean overdueOnly) {
        String normalized = view == null ? "MINE" : view.trim().toUpperCase();
        String predicate = switch (normalized) {
            case "STARTED" -> "instance.requester_user_id = ?";
            case "PROCESSED" -> "task.actual_actor_user_id = ? AND task.status <> 'OPEN'";
            default -> "task.assignee_user_id = ? AND task.status = 'OPEN'";
        };
        String overdue = overdueOnly ? " AND task.due_at < CURRENT_TIMESTAMP" : "";
        return jdbcTemplate.query("""
                SELECT task.id, task.workflow_instance_id, task.step_instance_id,
                       instance.resource_type, instance.resource_id, definition.name AS step_name,
                       instance.requester_user_id, task.original_approver_user_id,
                       task.assignee_user_id, task.status, task.due_at, task.completed_at, task.version_no
                FROM flowora_workflow_approval_task task
                JOIN flowora_workflow_instance instance ON instance.id = task.workflow_instance_id
                JOIN flowora_workflow_step_instance step_instance ON step_instance.id = task.step_instance_id
                JOIN flowora_workflow_step_definition definition ON definition.id = step_instance.step_definition_id
                WHERE task.organization_id = ? AND %s%s
                ORDER BY task.due_at IS NULL, task.due_at, task.created_at DESC LIMIT 500
                """.formatted(predicate, overdue), this::mapTask, actor.organizationId(), actor.userId());
    }

    @Transactional(readOnly = true)
    public InstanceView instance(String organizationId, String instanceId) {
        List<InstanceView> found = jdbcTemplate.query("""
                SELECT id, template_id, workflow_version_id, resource_type, resource_id,
                       resource_revision, requester_user_id, owner_user_id, status,
                       current_sequence_no, created_at, completed_at
                FROM flowora_workflow_instance WHERE organization_id = ? AND id = ?
                """, this::mapInstance, organizationId, instanceId);
        if (found.isEmpty()) notFound("workflowInstance", instanceId);
        return found.getFirst();
    }

    @Transactional(readOnly = true)
    public List<DecisionView> decisions(String organizationId, String instanceId) {
        instance(organizationId, instanceId);
        return jdbcTemplate.query("""
                SELECT id, action_code, original_approver_user_id, actual_actor_user_id,
                       comment_text, created_at
                FROM flowora_workflow_decision
                WHERE organization_id = ? AND workflow_instance_id = ?
                ORDER BY created_at, id
                """, (rs, row) -> new DecisionView(
                rs.getString("id"), rs.getString("action_code"),
                rs.getString("original_approver_user_id"), rs.getString("actual_actor_user_id"),
                rs.getString("comment_text"), rs.getTimestamp("created_at").toInstant()
        ), organizationId, instanceId);
    }

    @Transactional
    public TaskView actOnTask(
            FloworaPrincipal actor,
            String taskId,
            long expectedVersion,
            ActionRequest request,
            String requestId
    ) {
        TaskRow task = taskForUpdate(actor.organizationId(), taskId);
        if (task.version() != expectedVersion) optimisticConflict();
        if (!actor.userId().equals(task.assigneeUserId()) && !actor.permissions().contains("workflow:admin")) {
            throw new PlatformApiException(HttpStatus.FORBIDDEN, "WORKFLOW_TASK_FORBIDDEN",
                    "errors.workflowTaskForbidden");
        }
        if (!"OPEN".equals(task.status())) conflict("WORKFLOW_TASK_NOT_OPEN");
        if (request.action() == WorkflowAction.TRANSFER) {
            String target = blankToNull(request.transferToUserId());
            if (target == null) conflict("WORKFLOW_TRANSFER_TARGET_REQUIRED");
            approverResolver.requireEligibleUser(actor.organizationId(), target);
            updateTask(task, expectedVersion, "OPEN", target, actor.userId(), request.comment(), false);
            decision(task, actor.userId(), "TRANSFER", request.comment(), requestId);
            notifyTask(actor.organizationId(), task, target, "WORKFLOW_TASK_TRANSFERRED", "Approval task transferred");
            return task(actor.organizationId(), taskId);
        }
        if (request.action() != WorkflowAction.APPROVE
                && request.action() != WorkflowAction.REJECT
                && request.action() != WorkflowAction.RETURN) {
            conflict("WORKFLOW_TASK_ACTION_INVALID");
        }
        String taskStatus = switch (request.action()) {
            case APPROVE -> "APPROVED";
            case REJECT -> "REJECTED";
            case RETURN -> "RETURNED";
            default -> throw new IllegalStateException();
        };
        updateTask(task, expectedVersion, taskStatus, task.assigneeUserId(), actor.userId(), request.comment(), true);
        decision(task, actor.userId(), request.action().name(), request.comment(), requestId);
        jdbcTemplate.update("""
                UPDATE flowora_workflow_instance SET first_action_at = COALESCE(first_action_at, ?)
                WHERE id = ?
                """, Timestamp.from(Instant.now()), task.instanceId());

        if (request.action() == WorkflowAction.REJECT || request.action() == WorkflowAction.RETURN) {
            String instanceStatus = request.action() == WorkflowAction.REJECT ? "REJECTED" : "RETURNED";
            finishInstance(task.instanceId(), task.stepInstanceId(), instanceStatus);
            notifyRequester(task, "WORKFLOW_" + instanceStatus, "Workflow " + instanceStatus.toLowerCase());
        } else if (stepComplete(task.stepInstanceId(), task.completionMode())) {
            jdbcTemplate.update("""
                    UPDATE flowora_workflow_step_instance
                    SET status = 'COMPLETED', completed_at = ? WHERE id = ?
                    """, Timestamp.from(Instant.now()), task.stepInstanceId());
            if (task.completionMode() == CompletionMode.PARALLEL_ANY) {
                jdbcTemplate.update("""
                        UPDATE flowora_workflow_approval_task
                        SET status = 'CANCELLED', completed_at = ?, version_no = version_no + 1
                        WHERE step_instance_id = ? AND status = 'OPEN'
                        """, Timestamp.from(Instant.now()), task.stepInstanceId());
            }
            advance(task);
        }
        activity(actor.organizationId(), task.resourceType(), task.resourceId(), actor.userId(),
                "WORKFLOW_" + request.action().name(), request.comment(), requestId);
        return task(actor.organizationId(), taskId);
    }

    @Transactional
    public InstanceView actOnInstance(
            FloworaPrincipal actor,
            String instanceId,
            ActionRequest request,
            String requestId
    ) {
        InstanceRow row = instanceForUpdate(actor.organizationId(), instanceId);
        if (!"RUNNING".equals(row.status())) conflict("WORKFLOW_INSTANCE_NOT_RUNNING");
        if (request.action() == WorkflowAction.WITHDRAW) {
            if (!actor.userId().equals(row.requesterUserId()) || row.firstActionAt() != null) {
                throw new PlatformApiException(HttpStatus.CONFLICT, "WORKFLOW_WITHDRAW_NOT_ALLOWED",
                        "errors.workflowWithdrawNotAllowed");
            }
            finishInstance(instanceId, null, "WITHDRAWN");
        } else if (request.action() == WorkflowAction.TERMINATE) {
            if (!actor.permissions().contains("workflow:admin")) {
                throw new PlatformApiException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "errors.permissionDenied");
            }
            finishInstance(instanceId, null, "TERMINATED");
        } else {
            conflict("WORKFLOW_INSTANCE_ACTION_INVALID");
        }
        decision(new TaskRow(null, instanceId, null, null, null, row.resourceType(), row.resourceId(),
                row.requesterUserId(), null, null, 0), actor.userId(), request.action().name(),
                request.comment(), requestId);
        activity(actor.organizationId(), row.resourceType(), row.resourceId(), actor.userId(),
                "WORKFLOW_" + request.action().name(), request.comment(), requestId);
        return instance(actor.organizationId(), instanceId);
    }

    @Scheduled(fixedDelayString = "${flowora.workflow.sla-scan-delay-ms:60000}")
    @Transactional
    public void scanOverdueTasks() {
        List<TaskRow> overdue = jdbcTemplate.query("""
                SELECT task.id, task.workflow_instance_id, task.step_instance_id,
                       task.original_approver_user_id, task.assignee_user_id,
                       instance.resource_type, instance.resource_id, instance.requester_user_id,
                       step.completion_mode, task.status, task.version_no
                FROM flowora_workflow_approval_task task
                JOIN flowora_workflow_instance instance ON instance.id = task.workflow_instance_id
                JOIN flowora_workflow_step_instance step ON step.id = task.step_instance_id
                WHERE task.status = 'OPEN' AND task.due_at < ? AND task.sla_notified_at IS NULL
                LIMIT 200 FOR UPDATE SKIP LOCKED
                """, this::mapTaskRow, Timestamp.from(Instant.now()));
        for (TaskRow task : overdue) {
            outboxService.enqueue(taskOrganization(task.instanceId()), "WORKFLOW_TASK_OVERDUE", task.resourceType(),
                    task.resourceId(), task.assigneeUserId(), Map.of(
                            "title", "Approval task overdue", "message", task.resourceId()
                    ));
            jdbcTemplate.update("""
                    UPDATE flowora_workflow_approval_task SET sla_notified_at = ? WHERE id = ?
                    """, Timestamp.from(Instant.now()), task.id());
        }
    }

    private void activate(
            String instanceId,
            String organizationId,
            String requesterUserId,
            String ownerUserId,
            Map<String, Object> context,
            MatchedVersion version,
            int sequence
    ) {
        StepRequest step = version.steps().stream().filter(candidate -> candidate.sequence() == sequence)
                .findFirst().orElse(null);
        if (step == null) {
            jdbcTemplate.update("""
                    UPDATE flowora_workflow_instance
                    SET status = 'APPROVED', current_sequence_no = NULL, completed_at = ? WHERE id = ?
                    """, Timestamp.from(Instant.now()), instanceId);
            outboxService.enqueue(organizationId, "WORKFLOW_APPROVED", "WORKFLOW_INSTANCE", instanceId,
                    requesterUserId, Map.of("title", "Workflow approved", "message", instanceId));
            return;
        }
        String definitionId = jdbcTemplate.queryForObject("""
                SELECT id FROM flowora_workflow_step_definition
                WHERE workflow_version_id = ? AND step_key = ?
                """, String.class, version.versionId(), normalize(step.stepKey()));
        String stepInstanceId = UUID.randomUUID().toString();
        Instant dueAt = step.dueHours() == null ? null : Instant.now().plus(step.dueHours(), ChronoUnit.HOURS);
        jdbcTemplate.update("""
                INSERT INTO flowora_workflow_step_instance (
                    id, workflow_instance_id, step_definition_id, step_key, sequence_no,
                    completion_mode, status, due_at, activated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, stepInstanceId, instanceId, definitionId, normalize(step.stepKey()), sequence,
                step.completionMode().name(), timestamp(dueAt), Timestamp.from(Instant.now()));
        String departmentId = stringValue(context.get("departmentId"));
        List<ResolvedApprover> approvers = approverResolver.resolve(
                organizationId, requesterUserId, ownerUserId, departmentId,
                stringValue(context.get("resourceType")), step, version);
        for (ResolvedApprover approver : approvers) {
            String taskId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO flowora_workflow_approval_task (
                        id, organization_id, workflow_instance_id, step_instance_id,
                        original_approver_user_id, assignee_user_id, status, due_at
                    ) VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?)
                    """, taskId, organizationId, instanceId, stepInstanceId, approver.originalUserId(),
                    approver.assigneeUserId(), timestamp(dueAt));
            outboxService.enqueue(organizationId, "WORKFLOW_TASK_ASSIGNED", "WORKFLOW_INSTANCE",
                    instanceId, approver.assigneeUserId(), Map.of(
                            "title", step.name(), "message", "Approval required"
                    ));
        }
        jdbcTemplate.update("""
                UPDATE flowora_workflow_instance SET current_sequence_no = ? WHERE id = ?
                """, sequence, instanceId);
    }

    private void advance(TaskRow task) {
        VersionRuntime runtime = runtime(task.instanceId());
        int next = runtime.currentSequence() + 1;
        if (runtime.version().steps().stream().noneMatch(step -> step.sequence() == next)) {
            jdbcTemplate.update("""
                    UPDATE flowora_workflow_instance
                    SET status = 'APPROVED', current_sequence_no = NULL, completed_at = ? WHERE id = ?
                    """, Timestamp.from(Instant.now()), task.instanceId());
            notifyRequester(task, "WORKFLOW_APPROVED", "Workflow approved");
            projectionService.project(runtime.organizationId(), task.resourceType(), task.resourceId(), "APPROVED");
            return;
        }
        activate(task.instanceId(), runtime.organizationId(), runtime.requesterUserId(), runtime.ownerUserId(),
                runtime.context(), runtime.version(), next);
    }

    private VersionRuntime runtime(String instanceId) {
        return jdbcTemplate.queryForObject("""
                SELECT instance.organization_id, instance.requester_user_id, instance.owner_user_id,
                       instance.current_sequence_no, instance.snapshot_json,
                       instance.template_id, instance.workflow_version_id
                FROM flowora_workflow_instance instance WHERE instance.id = ?
                """, (rs, row) -> {
            Map<String, Object> snapshot = map(rs.getString("snapshot_json"));
            List<StepRequest> steps = objectMapper.convertValue(snapshot.get("steps"), new TypeReference<>() {});
            WorkflowV2Dtos.ConditionGroup condition = objectMapper.convertValue(
                    snapshot.get("condition"), WorkflowV2Dtos.ConditionGroup.class);
            boolean allowSelf = Boolean.TRUE.equals(snapshot.get("allowSelfApproval"));
            Map<String, Object> context = objectMapper.convertValue(snapshot.get("context"), new TypeReference<>() {});
            return new VersionRuntime(
                    rs.getString("organization_id"), rs.getString("requester_user_id"),
                    rs.getString("owner_user_id"), rs.getInt("current_sequence_no"), context,
                    new MatchedVersion(rs.getString("template_id"), rs.getString("workflow_version_id"),
                            0, condition, steps, allowSelf)
            );
        }, instanceId);
    }

    private boolean stepComplete(String stepInstanceId, CompletionMode mode) {
        if (mode == CompletionMode.PARALLEL_ANY) {
            Integer approved = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM flowora_workflow_approval_task
                    WHERE step_instance_id = ? AND status = 'APPROVED'
                    """, Integer.class, stepInstanceId);
            return approved != null && approved > 0;
        }
        Integer remaining = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flowora_workflow_approval_task
                WHERE step_instance_id = ? AND status <> 'APPROVED'
                """, Integer.class, stepInstanceId);
        return remaining != null && remaining == 0;
    }

    private void finishInstance(String instanceId, String stepInstanceId, String status) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE flowora_workflow_instance
                SET status = ?, current_sequence_no = NULL, completed_at = ? WHERE id = ?
                """, status, Timestamp.from(now), instanceId);
        jdbcTemplate.update("""
                UPDATE flowora_workflow_approval_task
                SET status = 'CANCELLED', completed_at = ?, version_no = version_no + 1
                WHERE workflow_instance_id = ? AND status = 'OPEN'
                """, Timestamp.from(now), instanceId);
        InstanceRow resource = jdbcTemplate.queryForObject("""
                SELECT id, resource_type, resource_id, requester_user_id, status, first_action_at
                FROM flowora_workflow_instance WHERE id = ?
                """, (rs, row) -> new InstanceRow(rs.getString("id"), rs.getString("resource_type"),
                rs.getString("resource_id"), rs.getString("requester_user_id"), rs.getString("status"),
                instant(rs, "first_action_at")), instanceId);
        projectionService.project(taskOrganization(instanceId), resource.resourceType(), resource.resourceId(), status);
        if (stepInstanceId == null) {
            jdbcTemplate.update("""
                    UPDATE flowora_workflow_step_instance
                    SET status = 'CANCELLED', completed_at = ?
                    WHERE workflow_instance_id = ? AND status = 'ACTIVE'
                    """, Timestamp.from(now), instanceId);
        } else {
            jdbcTemplate.update("""
                    UPDATE flowora_workflow_step_instance SET status = 'COMPLETED', completed_at = ? WHERE id = ?
                    """, Timestamp.from(now), stepInstanceId);
        }
    }

    private void updateTask(
            TaskRow task,
            long expectedVersion,
            String status,
            String assignee,
            String actor,
            String comment,
            boolean completed
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE flowora_workflow_approval_task
                SET status = ?, assignee_user_id = ?, actual_actor_user_id = ?,
                    decision_comment = ?, completed_at = ?, version_no = version_no + 1
                WHERE id = ? AND version_no = ?
                """, status, assignee, actor, comment, completed ? Timestamp.from(Instant.now()) : null,
                task.id(), expectedVersion);
        if (updated != 1) optimisticConflict();
    }

    private void decision(TaskRow task, String actor, String action, String comment, String requestId) {
        jdbcTemplate.update("""
                INSERT INTO flowora_workflow_decision (
                    id, organization_id, workflow_instance_id, step_instance_id, task_id,
                    action_code, original_approver_user_id, actual_actor_user_id, comment_text, request_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), taskOrganization(task.instanceId()), task.instanceId(),
                task.stepInstanceId(), task.id(), action, task.originalApproverUserId(), actor, comment, requestId);
    }

    private void notifyTask(String organizationId, TaskRow task, String recipient, String type, String title) {
        outboxService.enqueue(organizationId, type, task.resourceType(), task.resourceId(), recipient,
                Map.of("title", title, "message", task.resourceId()));
    }

    private void notifyRequester(TaskRow task, String type, String title) {
        notifyTask(taskOrganization(task.instanceId()), task, task.requesterUserId(), type, title);
    }

    private void activity(
            String organizationId,
            String resourceType,
            String resourceId,
            String actorUserId,
            String action,
            String summary,
            String requestId
    ) {
        jdbcTemplate.update("""
                INSERT INTO flowora_activity_event (
                    id, organization_id, resource_type, resource_id, actor_user_id,
                    action_code, summary, details_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), organizationId, normalize(resourceType), resourceId,
                actorUserId, action, summary, json(Map.of("requestId", requestId)));
        jdbcTemplate.update("""
                INSERT INTO flowora_audit_event (
                    id, organization_id, actor_user_id, action_code, resource_type,
                    resource_id, request_id, details_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), organizationId, actorUserId, action,
                normalize(resourceType), resourceId, requestId, json(Map.of("summary", summary)));
    }

    private TaskRow taskForUpdate(String organizationId, String taskId) {
        List<TaskRow> found = jdbcTemplate.query("""
                SELECT task.id, task.workflow_instance_id, task.step_instance_id,
                       task.original_approver_user_id, task.assignee_user_id,
                       instance.resource_type, instance.resource_id, instance.requester_user_id,
                       step.completion_mode, task.status, task.version_no
                FROM flowora_workflow_approval_task task
                JOIN flowora_workflow_instance instance ON instance.id = task.workflow_instance_id
                JOIN flowora_workflow_step_instance step ON step.id = task.step_instance_id
                WHERE task.organization_id = ? AND task.id = ? FOR UPDATE
                """, this::mapTaskRow, organizationId, taskId);
        if (found.isEmpty()) notFound("workflowTask", taskId);
        return found.getFirst();
    }

    private TaskView task(String organizationId, String taskId) {
        List<TaskView> found = jdbcTemplate.query("""
                SELECT task.id, task.workflow_instance_id, task.step_instance_id,
                       instance.resource_type, instance.resource_id, definition.name AS step_name,
                       instance.requester_user_id, task.original_approver_user_id,
                       task.assignee_user_id, task.status, task.due_at, task.completed_at, task.version_no
                FROM flowora_workflow_approval_task task
                JOIN flowora_workflow_instance instance ON instance.id = task.workflow_instance_id
                JOIN flowora_workflow_step_instance step_instance ON step_instance.id = task.step_instance_id
                JOIN flowora_workflow_step_definition definition ON definition.id = step_instance.step_definition_id
                WHERE task.organization_id = ? AND task.id = ?
                """, this::mapTask, organizationId, taskId);
        if (found.isEmpty()) notFound("workflowTask", taskId);
        return found.getFirst();
    }

    private TaskView mapTask(ResultSet rs, int row) throws SQLException {
        return new TaskView(
                rs.getString("id"), rs.getString("workflow_instance_id"), rs.getString("step_instance_id"),
                rs.getString("resource_type"), rs.getString("resource_id"), rs.getString("step_name"),
                rs.getString("requester_user_id"), rs.getString("original_approver_user_id"),
                rs.getString("assignee_user_id"), TaskStatus.valueOf(rs.getString("status")),
                instant(rs, "due_at"), instant(rs, "completed_at"), rs.getLong("version_no")
        );
    }

    private TaskRow mapTaskRow(ResultSet rs, int row) throws SQLException {
        return new TaskRow(
                rs.getString("id"), rs.getString("workflow_instance_id"), rs.getString("step_instance_id"),
                rs.getString("original_approver_user_id"), rs.getString("assignee_user_id"),
                rs.getString("resource_type"), rs.getString("resource_id"), rs.getString("requester_user_id"),
                CompletionMode.valueOf(rs.getString("completion_mode")), rs.getString("status"),
                rs.getLong("version_no")
        );
    }

    private InstanceView mapInstance(ResultSet rs, int row) throws SQLException {
        int sequence = rs.getInt("current_sequence_no");
        return new InstanceView(
                rs.getString("id"), rs.getString("template_id"), rs.getString("workflow_version_id"),
                rs.getString("resource_type"), rs.getString("resource_id"), rs.getInt("resource_revision"),
                rs.getString("requester_user_id"), rs.getString("owner_user_id"),
                InstanceStatus.valueOf(rs.getString("status")), rs.wasNull() ? null : sequence,
                rs.getTimestamp("created_at").toInstant(), instant(rs, "completed_at")
        );
    }

    private InstanceRow instanceForUpdate(String organizationId, String instanceId) {
        List<InstanceRow> found = jdbcTemplate.query("""
                SELECT id, resource_type, resource_id, requester_user_id, status, first_action_at
                FROM flowora_workflow_instance WHERE organization_id = ? AND id = ? FOR UPDATE
                """, (rs, row) -> new InstanceRow(
                rs.getString("id"), rs.getString("resource_type"), rs.getString("resource_id"),
                rs.getString("requester_user_id"), rs.getString("status"), instant(rs, "first_action_at")
        ), organizationId, instanceId);
        if (found.isEmpty()) notFound("workflowInstance", instanceId);
        return found.getFirst();
    }

    private String taskOrganization(String id) {
        return jdbcTemplate.queryForObject("""
                SELECT organization_id FROM flowora_workflow_instance WHERE id = ?
                """, String.class, id);
    }

    private Map<String, Object> map(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid workflow snapshot", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize workflow payload", exception);
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void optimisticConflict() {
        throw new PlatformApiException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT",
                "errors.optimisticLockConflict");
    }

    private void conflict(String code) {
        throw new PlatformApiException(HttpStatus.CONFLICT, code, "errors." + code.toLowerCase());
    }

    private void notFound(String resource, String id) {
        throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound",
                Map.of("resource", resource, "id", id));
    }

    private record TaskRow(
            String id,
            String instanceId,
            String stepInstanceId,
            String originalApproverUserId,
            String assigneeUserId,
            String resourceType,
            String resourceId,
            String requesterUserId,
            CompletionMode completionMode,
            String status,
            long version
    ) {
    }

    private record InstanceRow(
            String id,
            String resourceType,
            String resourceId,
            String requesterUserId,
            String status,
            Instant firstActionAt
    ) {
    }

    private record VersionRuntime(
            String organizationId,
            String requesterUserId,
            String ownerUserId,
            int currentSequence,
            Map<String, Object> context,
            MatchedVersion version
    ) {
    }
}

package com.flowora.erp.project;

import com.flowora.erp.common.api.WorkflowStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectTaskEntityTest {
    @Test
    void blockedTaskCanResumeButDoneTaskIsTerminal() {
        ProjectTaskEntity task = new ProjectTaskEntity("org-a", "project-a", null, "Prepare handover", null, "user-a", ProjectTaskPriority.HIGH, null, BigDecimal.TEN);

        task.transitionTo(ProjectTaskStatus.IN_PROGRESS);
        task.transitionTo(ProjectTaskStatus.BLOCKED);
        task.transitionTo(ProjectTaskStatus.IN_PROGRESS);
        task.transitionTo(ProjectTaskStatus.DONE);

        assertEquals(ProjectTaskStatus.DONE, task.status());
        assertThrows(WorkflowStateConflictException.class, () -> task.transitionTo(ProjectTaskStatus.IN_PROGRESS));
    }
}

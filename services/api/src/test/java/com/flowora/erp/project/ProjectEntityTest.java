package com.flowora.erp.project;

import com.flowora.erp.common.api.WorkflowStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectEntityTest {
    @Test
    void followsThePlannedActiveAtRiskCompletedLifecycle() {
        ProjectEntity project = new ProjectEntity("org-a", "PRJ-1", "Delivery", null, null, null, "user-a", LocalDate.now(), BigDecimal.ZERO, BigDecimal.ZERO, "USD");

        project.transitionTo(ProjectStatus.ACTIVE);
        project.transitionTo(ProjectStatus.AT_RISK);
        project.transitionTo(ProjectStatus.ACTIVE);
        project.transitionTo(ProjectStatus.COMPLETED);

        assertEquals(ProjectStatus.COMPLETED, project.status());
        assertThrows(WorkflowStateConflictException.class, () -> project.transitionTo(ProjectStatus.ACTIVE));
    }
}

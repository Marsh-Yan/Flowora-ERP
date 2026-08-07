package com.flowora.erp.workflow;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalPolicyTest {
    private final ApprovalPolicy policy = new ApprovalPolicy();

    @Test
    void requiresApprovalAtOrAboveTheConfiguredThreshold() {
        assertThat(policy.requiresApproval(
                WorkflowResourceType.PURCHASE_ORDER,
                new BigDecimal("10000"),
                new BigDecimal("10000")
        )).isTrue();
        assertThat(policy.requiresApproval(
                WorkflowResourceType.PURCHASE_ORDER,
                new BigDecimal("9999.99"),
                new BigDecimal("10000")
        )).isFalse();
    }

    @Test
    void projectTasksBypassApprovalInVersionOne() {
        assertThat(policy.requiresApproval(
                WorkflowResourceType.PROJECT,
                new BigDecimal("999999"),
                new BigDecimal("1")
        )).isFalse();
    }
}

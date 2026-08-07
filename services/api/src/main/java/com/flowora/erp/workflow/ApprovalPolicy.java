package com.flowora.erp.workflow;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ApprovalPolicy {
    public boolean requiresApproval(WorkflowResourceType resourceType, BigDecimal amount, BigDecimal threshold) {
        if (resourceType == WorkflowResourceType.GENERAL || resourceType == WorkflowResourceType.PROJECT) {
            return false;
        }
        return amount.compareTo(threshold) >= 0;
    }

    public String approverRole(WorkflowResourceType resourceType) {
        return resourceType == WorkflowResourceType.INVENTORY_ADJUSTMENT ? "MANAGEMENT" : "MANAGEMENT";
    }
}

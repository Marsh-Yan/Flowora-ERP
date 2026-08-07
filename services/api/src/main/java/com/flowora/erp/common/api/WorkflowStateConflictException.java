package com.flowora.erp.common.api;

public class WorkflowStateConflictException extends RuntimeException {
    public WorkflowStateConflictException(String message) {
        super(message);
    }
}

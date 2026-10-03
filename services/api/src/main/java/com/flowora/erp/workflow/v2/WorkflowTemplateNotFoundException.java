package com.flowora.erp.workflow.v2;

import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.http.HttpStatus;
import java.util.Map;

/** Only the read-only, pre-write template match may signal a safe compatibility fallback. */
public class WorkflowTemplateNotFoundException extends PlatformApiException {
    public WorkflowTemplateNotFoundException(String resourceType) {
        super(HttpStatus.CONFLICT, "WORKFLOW_TEMPLATE_NOT_FOUND", "errors.workflowTemplateNotFound",
                Map.of("resourceType", resourceType));
    }
}

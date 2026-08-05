package com.flowora.erp.common.api;

public class ResourceNotFoundException extends RuntimeException {
    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(resourceType + " not found: " + resourceId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }
}

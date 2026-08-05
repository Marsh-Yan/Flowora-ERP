package com.flowora.erp.common.api;

public class MasterDataConflictException extends RuntimeException {
    private final String resourceType;
    private final String codeValue;

    public MasterDataConflictException(String resourceType, String codeValue) {
        super(resourceType + " code already exists: " + codeValue);
        this.resourceType = resourceType;
        this.codeValue = codeValue;
    }

    public String resourceType() {
        return resourceType;
    }

    public String codeValue() {
        return codeValue;
    }
}

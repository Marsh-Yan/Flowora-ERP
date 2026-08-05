package com.flowora.erp.common.api;

import java.util.UUID;

public record ApiResponse<T>(T data, String requestId) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, "req_" + UUID.randomUUID());
    }

    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, requestId);
    }
}

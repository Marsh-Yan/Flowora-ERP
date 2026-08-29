package com.flowora.erp.common.api;

import java.util.Map;
import java.util.List;

public record ApiError(
        String code,
        String messageKey,
        Map<String, Object> args,
        List<ApiFieldError> fieldErrors,
        String requestId
) {
    public ApiError(String code, String messageKey, Map<String, Object> args, String requestId) {
        this(code, messageKey, args, List.of(), requestId);
    }
}

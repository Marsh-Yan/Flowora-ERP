package com.flowora.erp.common.api;

import java.util.Map;

public record ApiError(
        String code,
        String messageKey,
        Map<String, Object> args,
        String requestId
) {
}

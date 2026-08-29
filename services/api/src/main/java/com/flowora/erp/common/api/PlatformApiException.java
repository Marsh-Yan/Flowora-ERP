package com.flowora.erp.common.api;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class PlatformApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final Map<String, Object> args;

    public PlatformApiException(HttpStatus status, String code, String messageKey) {
        this(status, code, messageKey, Map.of());
    }

    public PlatformApiException(HttpStatus status, String code, String messageKey, Map<String, Object> args) {
        super(code);
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.args = Map.copyOf(args);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String messageKey() { return messageKey; }
    public Map<String, Object> args() { return args; }
}

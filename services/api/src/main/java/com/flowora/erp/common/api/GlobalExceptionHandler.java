package com.flowora.erp.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import jakarta.validation.ConstraintViolationException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PlatformApiException.class)
    public ResponseEntity<ApiError> handlePlatformApiException(
            PlatformApiException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.status()).body(new ApiError(
                exception.code(),
                exception.messageKey(),
                exception.args(),
                RequestIdFilter.get(request)
        ));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(
                v2(request) ? "INVALID_CREDENTIALS" : "AUTH_INVALID_CREDENTIALS",
                "errors.authInvalidCredentials",
                Map.of(),
                RequestIdFilter.get(request)
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(
                "RESOURCE_NOT_FOUND",
                "errors.resourceNotFound",
                Map.of("resource", exception.resourceType(), "id", exception.resourceId()),
                RequestIdFilter.get(request)
        ));
    }

    @ExceptionHandler(MasterDataConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            MasterDataConflictException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "MASTER_DATA_CONFLICT",
                "errors.masterDataConflict",
                Map.of("resource", exception.resourceType(), "code", exception.codeValue()),
                RequestIdFilter.get(request)
        ));
    }

    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, Object> args = new HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                args.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
                v2(request) ? "VALIDATION_FAILED" : "VALIDATION_ERROR",
                "errors.validation",
                args,
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> new ApiFieldError(error.getField(), "INVALID", "errors.validation"))
                        .toList(),
                RequestIdFilter.get(request)
        ));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        if (exception instanceof MethodArgumentNotValidException validation) {
            var response = handleValidation(validation, request);
            return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
        }
        String code = status.value() == 404 ? "RESOURCE_NOT_FOUND" : status.is4xxClientError() ? "BAD_REQUEST" : "INTERNAL_ERROR";
        String messageKey = status.value() == 404 ? "errors.resourceNotFound" : status.is4xxClientError() ? "errors.badRequest" : "errors.internal";
        if (status.is5xxServerError()) log.error("Framework API exception, requestId={}", RequestIdFilter.get(request), exception);
        return new ResponseEntity<>(new ApiError(code, messageKey, Map.of(), RequestIdFilter.get(request)), headers, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "errors.validation", Map.of(), RequestIdFilter.get(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
                "BAD_REQUEST",
                "errors.badRequest",
                Map.of("reason", exception.getMessage() == null ? "Invalid request" : exception.getMessage()),
                RequestIdFilter.get(request)
        ));
    }

    @ExceptionHandler(WorkflowStateConflictException.class)
    public ResponseEntity<ApiError> handleWorkflowConflict(
            WorkflowStateConflictException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "WORKFLOW_STATE_CONFLICT",
                "errors.workflowStateConflict",
                Map.of("reason", exception.getMessage() == null ? "Invalid workflow transition" : exception.getMessage()),
                RequestIdFilter.get(request)
        ));
    }

    @ExceptionHandler(WorkflowPermissionException.class)
    public ResponseEntity<ApiError> handleWorkflowPermission(
            WorkflowPermissionException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
                "WORKFLOW_FORBIDDEN",
                "errors.workflowForbidden",
                Map.of("reason", exception.getMessage() == null ? "Workflow action is not allowed" : exception.getMessage()),
                RequestIdFilter.get(request)
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
                v2(request) ? "PERMISSION_DENIED" : "AUTH_FORBIDDEN",
                "errors.authForbidden",
                Map.of(),
                RequestIdFilter.get(request)
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleStateConflict(
            IllegalStateException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "STATE_CONFLICT",
                "errors.stateConflict",
                Map.of("reason", exception.getMessage() == null ? "The current state does not allow this operation" : exception.getMessage()),
                RequestIdFilter.get(request)
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = RequestIdFilter.get(request);
        log.error("Unhandled API exception, requestId={}", requestId, exception);
        ApiError error = new ApiError(
                "INTERNAL_ERROR",
                "errors.internal",
                Map.of(),
                requestId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private boolean v2(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v2/");
    }
}

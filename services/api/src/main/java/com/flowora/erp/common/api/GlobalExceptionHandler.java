package com.flowora.erp.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(
                "AUTH_INVALID_CREDENTIALS",
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, Object> args = new HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                args.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
                "VALIDATION_ERROR",
                "errors.validation",
                args,
                RequestIdFilter.get(request)
        ));
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
        ApiError error = new ApiError(
                "INTERNAL_ERROR",
                "errors.internal",
                Map.of(),
                requestId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

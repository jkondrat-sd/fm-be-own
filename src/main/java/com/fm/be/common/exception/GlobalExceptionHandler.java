package com.fm.be.common.exception;

import com.fm.be.common.constants.ErrorCode;
import com.fm.be.common.dto.ApiError;
import com.fm.be.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler that converts all exceptions into the
 * standard API response envelope with appropriate HTTP status codes.
 *
 * <p>Handles: validation errors, business rule violations, resource not found,
 * duplicate resources, rate limiting, access denied, and generic exceptions.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Jakarta Bean Validation failures on DTOs.
     * Returns a 400 with VALIDATION_ERROR code and per-field details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation failed: {}", fieldErrors);

        ApiError apiError = ApiError.builder()
                .code(ErrorCode.VALIDATION_ERROR.name())
                .message("Request validation failed")
                .details(fieldErrors)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(apiError));
    }

    /**
     * Handles resource not found exceptions.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());

        ApiError apiError = ApiError.builder()
                .code(ex.getErrorCode().name())
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(apiError));
    }

    /**
     * Handles duplicate resource exceptions (unique constraint violations).
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResourceException(
            DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());

        ApiError apiError = ApiError.builder()
                .code(ex.getErrorCode().name())
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(apiError));
    }

    /**
     * Handles business rule violations (geofence, face mismatch, payroll locked, etc.).
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRuleException(
            BusinessRuleException ex) {
        log.warn("Business rule violation [{}]: {}", ex.getErrorCode().name(), ex.getMessage());

        ApiError apiError = ApiError.builder()
                .code(ex.getErrorCode().name())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .build();

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(apiError));
    }

    /**
     * Handles rate limit exceeded exceptions.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceededException(
            RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());

        ApiError apiError = ApiError.builder()
                .code(ex.getErrorCode().name())
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(apiError));
    }

    /**
     * Handles Spring Security access denied (insufficient role/permission).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        ApiError apiError = ApiError.builder()
                .code(ErrorCode.AUTH_UNAUTHORIZED.name())
                .message(ErrorCode.AUTH_UNAUTHORIZED.getDefaultMessage())
                .build();

        return ResponseEntity
                .status(ErrorCode.AUTH_UNAUTHORIZED.getHttpStatus())
                .body(ApiResponse.error(apiError));
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * Logs the full stack trace but returns a generic error message to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);

        ApiError apiError = ApiError.builder()
                .code(ErrorCode.SERVER_ERROR.name())
                .message(ErrorCode.SERVER_ERROR.getDefaultMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(apiError));
    }
}

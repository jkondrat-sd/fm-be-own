package com.fm.be.common.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * All error codes shared with FE clients (Mobile, Web, Client Portal).
 * Each code maps to a specific HTTP status for consistent error responses.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- Authentication & Authorization ---
    AUTH_TOKEN_EXPIRED("JWT expired, trigger refresh", HttpStatus.UNAUTHORIZED),
    AUTH_UNAUTHORIZED("Insufficient role/permission", HttpStatus.FORBIDDEN),
    AUTH_INVALID_CREDENTIALS("Invalid email or password", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_OTP("Invalid or expired OTP", HttpStatus.UNAUTHORIZED),

    // --- Business Rules ---
    GEOFENCE_VIOLATION("Employee is outside designated site boundary", HttpStatus.UNPROCESSABLE_ENTITY),
    FACE_MISMATCH("Facial recognition failed", HttpStatus.UNPROCESSABLE_ENTITY),
    SHIFT_NOT_STARTED("Too early or late for punch", HttpStatus.UNPROCESSABLE_ENTITY),
    DUPLICATE_PUNCH("Idempotent punch already exists", HttpStatus.CONFLICT),
    PAYROLL_LOCKED("Payroll period is locked", HttpStatus.LOCKED),
    SITE_INACTIVE("Site has been deactivated", HttpStatus.GONE),

    // --- General ---
    RATE_LIMITED("Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    VALIDATION_ERROR("Request validation failed", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("Requested resource not found", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE("Resource already exists", HttpStatus.CONFLICT),
    SERVER_ERROR("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String defaultMessage;
    private final HttpStatus httpStatus;
}

package com.fm.be.common.exception;

import com.fm.be.common.constants.ErrorCode;
import lombok.Getter;

import java.util.Map;

/**
 * Thrown when a domain-specific business rule is violated.
 * Carries the specific {@link ErrorCode} and optional details map.
 *
 * <p>Examples: geofence violation, face mismatch, shift not started,
 * payroll locked, duplicate punch, etc.</p>
 */
@Getter
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessRuleException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessRuleException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}

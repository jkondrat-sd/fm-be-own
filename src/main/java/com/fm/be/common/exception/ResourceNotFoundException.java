package com.fm.be.common.exception;

import com.fm.be.common.constants.ErrorCode;
import lombok.Getter;

/**
 * Thrown when a requested resource is not found.
 * Results in a 404 response with RESOURCE_NOT_FOUND error code.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final ErrorCode errorCode;

    public ResourceNotFoundException(String message) {
        super(message);
        this.errorCode = ErrorCode.RESOURCE_NOT_FOUND;
    }

    public ResourceNotFoundException(String entityName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", entityName, fieldName, fieldValue));
        this.errorCode = ErrorCode.RESOURCE_NOT_FOUND;
    }
}

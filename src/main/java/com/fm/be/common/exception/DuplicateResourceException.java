package com.fm.be.common.exception;

import com.fm.be.common.constants.ErrorCode;
import lombok.Getter;

/**
 * Thrown when attempting to create a resource that already exists.
 * Results in a 409 response with DUPLICATE_RESOURCE error code.
 */
@Getter
public class DuplicateResourceException extends RuntimeException {

    private final ErrorCode errorCode;

    public DuplicateResourceException(String message) {
        super(message);
        this.errorCode = ErrorCode.DUPLICATE_RESOURCE;
    }

    public DuplicateResourceException(String entityName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", entityName, fieldName, fieldValue));
        this.errorCode = ErrorCode.DUPLICATE_RESOURCE;
    }
}

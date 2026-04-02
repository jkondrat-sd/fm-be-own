package com.fm.be.common.exception;

import com.fm.be.common.constants.ErrorCode;
import lombok.Getter;

/**
 * Thrown when a rate limit is exceeded (Redis-backed counter).
 * Results in a 429 response with RATE_LIMITED error code.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final ErrorCode errorCode;

    public RateLimitExceededException(String message) {
        super(message);
        this.errorCode = ErrorCode.RATE_LIMITED;
    }
}

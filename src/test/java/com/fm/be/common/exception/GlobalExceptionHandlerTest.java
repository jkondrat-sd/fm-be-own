package com.fm.be.common.exception;

import com.fm.be.common.constants.ErrorCode;
import com.fm.be.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 * Verifies each exception type maps to the correct HTTP status and error code.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleValidationException_returnsBadRequest() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "dto");
        bindingResult.addError(new FieldError("dto", "email", "must not be blank"));
        bindingResult.addError(new FieldError("dto", "phone", "must not be null"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("VALIDATION_ERROR", response.getBody().getError().getCode());
        assertNotNull(response.getBody().getError().getDetails());
        assertTrue(response.getBody().getError().getDetails().containsKey("email"));
        assertTrue(response.getBody().getError().getDetails().containsKey("phone"));
    }

    @Test
    void handleResourceNotFoundException_returnsNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "id", "abc-123");

        ResponseEntity<ApiResponse<Void>> response = handler.handleResourceNotFoundException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getError().getCode());
        assertTrue(response.getBody().getError().getMessage().contains("User"));
    }

    @Test
    void handleDuplicateResourceException_returnsConflict() {
        DuplicateResourceException ex = new DuplicateResourceException("User", "email", "a@b.com");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDuplicateResourceException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("DUPLICATE_RESOURCE", response.getBody().getError().getCode());
    }

    @Test
    void handleBusinessRuleException_returnsCorrectStatus() {
        Map<String, Object> details = Map.of("distance_meters", 150);
        BusinessRuleException ex = new BusinessRuleException(
                ErrorCode.GEOFENCE_VIOLATION,
                "Employee is outside designated site boundary",
                details
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessRuleException(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("GEOFENCE_VIOLATION", response.getBody().getError().getCode());
        assertEquals(150, response.getBody().getError().getDetails().get("distance_meters"));
    }

    @Test
    void handleBusinessRuleException_payrollLocked_returns423() {
        BusinessRuleException ex = new BusinessRuleException(ErrorCode.PAYROLL_LOCKED);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessRuleException(ex);

        assertEquals(HttpStatus.LOCKED, response.getStatusCode());
        assertEquals("PAYROLL_LOCKED", response.getBody().getError().getCode());
    }

    @Test
    void handleRateLimitExceededException_returns429() {
        RateLimitExceededException ex = new RateLimitExceededException("Too many OTP requests");

        ResponseEntity<ApiResponse<Void>> response = handler.handleRateLimitExceededException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("RATE_LIMITED", response.getBody().getError().getCode());
    }

    @Test
    void handleAccessDeniedException_returnsForbidden() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDeniedException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("AUTH_UNAUTHORIZED", response.getBody().getError().getCode());
    }

    @Test
    void handleGenericException_returns500() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("SERVER_ERROR", response.getBody().getError().getCode());
        // Should NOT expose internal error details to client
        assertEquals("An unexpected error occurred", response.getBody().getError().getMessage());
    }
}

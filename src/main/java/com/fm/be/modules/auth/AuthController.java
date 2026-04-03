package com.fm.be.modules.auth;

import com.fm.be.modules.auth.dto.LoginRequestDto;
import com.fm.be.modules.auth.dto.LoginResponseDto;
import com.fm.be.modules.auth.dto.OtpRequestDto;
import com.fm.be.modules.auth.dto.OtpVerifyDto;
import com.fm.be.modules.auth.dto.TokenRefreshDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for authentication endpoints.
 * All endpoints are publicly accessible (no JWT required).
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "OTP login (mobile), email/password login (web), token refresh, logout")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;

    /**
     * Requests a 6-digit OTP to be sent to the given phone number.
     * Rate limited to 5 requests per minute per phone.
     *
     * @param request the OTP request with phone number
     * @return 200 with success message
     */
    @Operation(
            summary = "Request OTP",
            description = "Sends a 6-digit OTP to the given phone number for mobile login. Rate limited to 5/min.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OTP sent successfully"),
                    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
            })
    @PostMapping("/otp/request")
    public Map<String, String> requestOtp(@Valid @RequestBody OtpRequestDto request) {
        authService.requestOtp(request);
        return Map.of("message", "OTP sent successfully");
    }

    /**
     * Verifies the OTP and returns JWT access + refresh tokens.
     *
     * @param request the OTP verify request with phone and OTP
     * @return login response with tokens
     */
    @Operation(
            summary = "Verify OTP",
            description = "Validates the OTP and returns JWT access + refresh tokens for mobile login.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OTP verified, tokens returned"),
                    @ApiResponse(responseCode = "401", description = "Invalid or expired OTP")
            })
    @PostMapping("/otp/verify")
    public LoginResponseDto verifyOtp(@Valid @RequestBody OtpVerifyDto request) {
        return authService.verifyOtp(request);
    }

    /**
     * Authenticates with email and password (web/client portal).
     *
     * @param request the login request with email and password
     * @return login response with tokens
     */
    @Operation(
            summary = "Email/password login",
            description = "Authenticates via email and password (web/client portal). Returns JWT access + refresh tokens.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful, tokens returned"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials")
            })
    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    /**
     * Refreshes the access token using a valid refresh token.
     *
     * @param request the refresh request with refresh token
     * @return login response with new access token
     */
    @Operation(
            summary = "Refresh access token",
            description = "Issues a new access token using a valid refresh token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "New access token issued"),
                    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
            })
    @PostMapping("/refresh")
    public LoginResponseDto refreshToken(
            @Valid @RequestBody TokenRefreshDto request) {
        return authService.refreshToken(request);
    }

    /**
     * Logs out by revoking the refresh token.
     *
     * @param request the refresh token to revoke
     * @return 200 with success message
     */
    @Operation(
            summary = "Logout",
            description = "Revokes the refresh token, invalidating the session.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Logged out successfully")
            })
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @Valid @RequestBody TokenRefreshDto request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}

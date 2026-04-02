package com.fm.be.modules.auth;

import com.fm.be.modules.auth.dto.LoginRequestDto;
import com.fm.be.modules.auth.dto.LoginResponseDto;
import com.fm.be.modules.auth.dto.OtpRequestDto;
import com.fm.be.modules.auth.dto.OtpVerifyDto;
import com.fm.be.modules.auth.dto.TokenRefreshDto;
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
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/auth/otp/request — Request OTP for mobile login</li>
 *   <li>POST /v1/auth/otp/verify — Verify OTP and get tokens</li>
 *   <li>POST /v1/auth/login — Email/password login</li>
 *   <li>POST /v1/auth/refresh — Refresh access token</li>
 *   <li>POST /v1/auth/logout — Revoke refresh token</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Requests a 6-digit OTP to be sent to the given phone number.
     * Rate limited to 5 requests per minute per phone.
     *
     * @param request the OTP request with phone number
     * @return 200 with success message
     */
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
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @Valid @RequestBody TokenRefreshDto request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}

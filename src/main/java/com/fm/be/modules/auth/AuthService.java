package com.fm.be.modules.auth;

import com.fm.be.common.constants.ErrorCode;
import com.fm.be.common.exception.BusinessRuleException;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.common.security.JwtTokenProvider;
import com.fm.be.modules.auth.dto.LoginRequestDto;
import com.fm.be.modules.auth.dto.LoginResponseDto;
import com.fm.be.modules.auth.dto.OtpRequestDto;
import com.fm.be.modules.auth.dto.OtpVerifyDto;
import com.fm.be.modules.auth.dto.TokenRefreshDto;
import com.fm.be.modules.auth.entity.RefreshToken;
import com.fm.be.modules.users.UserRepository;
import com.fm.be.modules.users.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Service handling all authentication flows:
 * <ul>
 *   <li>OTP login (mobile) — request and verify OTP</li>
 *   <li>Email/password login (web/client portal)</li>
 *   <li>Token refresh — issue new access token from valid refresh token</li>
 *   <li>Logout — revoke refresh token</li>
 * </ul>
 *
 * <p>OTPs are stored in Redis with a 5-minute TTL. Rate limiting on OTP
 * requests is enforced at 5 per minute per phone number via Redis counters.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String OTP_RATE_KEY_PREFIX = "otp_rate:";
    private static final Duration OTP_TTL = Duration.ofSeconds(300);
    private static final Duration OTP_RATE_WINDOW = Duration.ofMinutes(1);
    private static final int OTP_MAX_REQUESTS_PER_MINUTE = 5;
    private static final int OTP_LENGTH = 6;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    /**
     * Requests an OTP for the given phone number.
     * Generates a 6-digit OTP, stores it in Redis with 5-minute TTL,
     * and sends it via SMS gateway (simulated via logging in V1).
     *
     * @param request the OTP request containing the phone number
     * @throws BusinessRuleException if rate limit exceeded
     */
    public void requestOtp(OtpRequestDto request) {
        String phone = request.getPhone();
        checkOtpRateLimit(phone);

        String otp = generateOtp();
        String redisKey = OTP_KEY_PREFIX + phone;

        redisTemplate.opsForValue().set(redisKey, otp, OTP_TTL);

        // TODO: Integrate with actual SMS gateway (Twilio/MSG91)
        // For V1, log the OTP for development purposes
        log.info("OTP generated for phone {}: {}", phone, otp);
    }

    /**
     * Verifies an OTP and returns JWT tokens on success.
     * If the user doesn't exist, this does NOT auto-create (admin must create users).
     *
     * @param request the OTP verify request
     * @return login response with JWT tokens
     * @throws BusinessRuleException if OTP is invalid or expired
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public LoginResponseDto verifyOtp(OtpVerifyDto request) {
        String phone = request.getPhone();
        String redisKey = OTP_KEY_PREFIX + phone;

        String storedOtp = redisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null || !storedOtp.equals(request.getOtp())) {
            throw new BusinessRuleException(ErrorCode.AUTH_INVALID_OTP,
                    "Invalid or expired OTP");
        }

        // OTP is valid — delete it (one-time use)
        redisTemplate.delete(redisKey);

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "phone", phone));

        if (!user.isActive()) {
            throw new BusinessRuleException(ErrorCode.AUTH_UNAUTHORIZED,
                    "User account is deactivated");
        }

        return generateTokensAndResponse(user, request.getDeviceInfo());
    }

    /**
     * Authenticates a user with email and password (web/client portal).
     *
     * @param request the login request with email and password
     * @return login response with JWT tokens
     * @throws BusinessRuleException if credentials are invalid
     */
    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessRuleException(
                        ErrorCode.AUTH_INVALID_CREDENTIALS,
                        "Invalid email or password"));

        if (!user.isActive()) {
            throw new BusinessRuleException(ErrorCode.AUTH_UNAUTHORIZED,
                    "User account is deactivated");
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(),
                        user.getPasswordHash())) {
            throw new BusinessRuleException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "Invalid email or password");
        }

        return generateTokensAndResponse(user, request.getDeviceInfo());
    }

    /**
     * Issues a new access token using a valid, non-expired refresh token.
     *
     * @param request the refresh request containing the refresh token
     * @return login response with new access token and same refresh token
     * @throws BusinessRuleException if refresh token is invalid or expired
     */
    @Transactional(readOnly = true)
    public LoginResponseDto refreshToken(TokenRefreshDto request) {
        RefreshToken storedToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BusinessRuleException(
                        ErrorCode.AUTH_TOKEN_EXPIRED,
                        "Invalid or expired refresh token"));

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            // Delete expired token and reject
            refreshTokenRepository.delete(storedToken);
            throw new BusinessRuleException(ErrorCode.AUTH_TOKEN_EXPIRED,
                    "Refresh token has expired");
        }

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", storedToken.getUserId()));

        // Generate new access token only; keep same refresh token
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getRole(), user.getOrganizationId(),
                List.of());

        return LoginResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .organizationId(user.getOrganizationId())
                .accessToken(accessToken)
                .refreshToken(storedToken.getToken())
                .build();
    }

    /**
     * Logs out the user by deleting the specified refresh token.
     *
     * @param refreshToken the refresh token to revoke
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
        log.info("Refresh token revoked");
    }

    /**
     * Generates JWT access + refresh tokens, persists the refresh token,
     * and builds the login response DTO.
     */
    private LoginResponseDto generateTokensAndResponse(User user,
                                                       String deviceInfo) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getRole(), user.getOrganizationId(),
                List.of());

        String refreshTokenStr = jwtTokenProvider.generateRefreshToken(
                user.getId());

        // Persist refresh token for later validation
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshTokenStr)
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .deviceInfo(deviceInfo)
                .build();

        refreshTokenRepository.save(refreshToken);

        return LoginResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .organizationId(user.getOrganizationId())
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .build();
    }

    /**
     * Checks the OTP rate limit for the given phone.
     * Uses Redis atomic increment with a 1-minute expiry window.
     *
     * @throws BusinessRuleException if rate limit exceeded (> 5/min)
     */
    private void checkOtpRateLimit(String phone) {
        String rateLimitKey = OTP_RATE_KEY_PREFIX + phone;

        Long currentCount = redisTemplate.opsForValue().increment(rateLimitKey);

        if (currentCount != null && currentCount == 1) {
            // First request in this window — set expiry
            redisTemplate.expire(rateLimitKey, OTP_RATE_WINDOW);
        }

        if (currentCount != null && currentCount > OTP_MAX_REQUESTS_PER_MINUTE) {
            throw new BusinessRuleException(ErrorCode.RATE_LIMITED,
                    "Too many OTP requests. Please try again later.",
                    Map.of("retryAfterSeconds", 60));
        }
    }

    /**
     * Generates a secure 6-digit OTP string.
     *
     * @return 6-digit zero-padded OTP
     */
    private String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}

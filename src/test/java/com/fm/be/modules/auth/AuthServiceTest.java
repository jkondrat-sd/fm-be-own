package com.fm.be.modules.auth;

import com.fm.be.common.constants.ErrorCode;
import com.fm.be.common.constants.Role;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}.
 * Covers OTP request/verify, email/password login, token refresh, and logout.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(userId)
                .phone("+919876543210")
                .email("test@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .organizationId(orgId)
                .build();
    }

    @Nested
    @DisplayName("requestOtp")
    class RequestOtpTests {

        @Test
        @DisplayName("should store OTP in Redis and succeed")
        void requestOtp_validPhone_storesOtpInRedis() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(1L);

            OtpRequestDto request = OtpRequestDto.builder()
                    .phone("+919876543210")
                    .build();

            authService.requestOtp(request);

            // Verify OTP stored in Redis with TTL
            verify(valueOperations).set(
                    eq("otp:+919876543210"),
                    anyString(),
                    eq(Duration.ofSeconds(300)));
        }

        @Test
        @DisplayName("should throw RATE_LIMITED when OTP rate limit exceeded")
        void requestOtp_rateLimitExceeded_throwsException() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("otp_rate:+919876543210")).thenReturn(6L);

            OtpRequestDto request = OtpRequestDto.builder()
                    .phone("+919876543210")
                    .build();

            assertThatThrownBy(() -> authService.requestOtp(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    });
        }

        @Test
        @DisplayName("should set expiry on first OTP request in window")
        void requestOtp_firstRequest_setsExpiry() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("otp_rate:+919876543210")).thenReturn(1L);

            OtpRequestDto request = OtpRequestDto.builder()
                    .phone("+919876543210")
                    .build();

            authService.requestOtp(request);

            verify(redisTemplate).expire("otp_rate:+919876543210",
                    Duration.ofMinutes(1));
        }
    }

    @Nested
    @DisplayName("verifyOtp")
    class VerifyOtpTests {

        @Test
        @DisplayName("should return tokens on valid OTP")
        void verifyOtp_validOtp_returnsTokens() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("otp:+919876543210")).thenReturn("123456");
            when(userRepository.findByPhone("+919876543210"))
                    .thenReturn(Optional.of(testUser));
            when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                    .thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(any()))
                    .thenReturn("refresh-token");
            when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OtpVerifyDto request = OtpVerifyDto.builder()
                    .phone("+919876543210")
                    .otp("123456")
                    .build();

            LoginResponseDto response = authService.verifyOtp(request);

            assertThat(response.getUserId()).isEqualTo(userId);
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getRole()).isEqualTo(Role.EMPLOYEE);

            // OTP should be deleted after use
            verify(redisTemplate).delete("otp:+919876543210");
        }

        @Test
        @DisplayName("should throw AUTH_INVALID_OTP when OTP is wrong")
        void verifyOtp_wrongOtp_throwsException() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("otp:+919876543210")).thenReturn("123456");

            OtpVerifyDto request = OtpVerifyDto.builder()
                    .phone("+919876543210")
                    .otp("999999")
                    .build();

            assertThatThrownBy(() -> authService.verifyOtp(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_OTP);
                    });
        }

        @Test
        @DisplayName("should throw AUTH_INVALID_OTP when OTP is expired (null)")
        void verifyOtp_expiredOtp_throwsException() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("otp:+919876543210")).thenReturn(null);

            OtpVerifyDto request = OtpVerifyDto.builder()
                    .phone("+919876543210")
                    .otp("123456")
                    .build();

            assertThatThrownBy(() -> authService.verifyOtp(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_OTP);
                    });
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void verifyOtp_userNotFound_throwsException() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("otp:+919876543210")).thenReturn("123456");
            when(userRepository.findByPhone("+919876543210"))
                    .thenReturn(Optional.empty());

            OtpVerifyDto request = OtpVerifyDto.builder()
                    .phone("+919876543210")
                    .otp("123456")
                    .build();

            assertThatThrownBy(() -> authService.verifyOtp(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw AUTH_UNAUTHORIZED when user is deactivated")
        void verifyOtp_deactivatedUser_throwsException() {
            testUser.setActive(false);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("otp:+919876543210")).thenReturn("123456");
            when(userRepository.findByPhone("+919876543210"))
                    .thenReturn(Optional.of(testUser));

            OtpVerifyDto request = OtpVerifyDto.builder()
                    .phone("+919876543210")
                    .otp("123456")
                    .build();

            assertThatThrownBy(() -> authService.verifyOtp(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode()).isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
                    });
        }
    }

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("should return tokens on valid email/password")
        void login_validCredentials_returnsTokens() {
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("password", "$2a$10$hashedpassword"))
                    .thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                    .thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(any()))
                    .thenReturn("refresh-token");
            when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            LoginRequestDto request = LoginRequestDto.builder()
                    .email("test@example.com")
                    .password("password")
                    .build();

            LoginResponseDto response = authService.login(request);

            assertThat(response.getUserId()).isEqualTo(userId);
            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("should throw AUTH_INVALID_CREDENTIALS when email not found")
        void login_emailNotFound_throwsException() {
            when(userRepository.findByEmail("wrong@example.com"))
                    .thenReturn(Optional.empty());

            LoginRequestDto request = LoginRequestDto.builder()
                    .email("wrong@example.com")
                    .password("password")
                    .build();

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode())
                                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
                    });
        }

        @Test
        @DisplayName("should throw AUTH_INVALID_CREDENTIALS when password is wrong")
        void login_wrongPassword_throwsException() {
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongpassword", "$2a$10$hashedpassword"))
                    .thenReturn(false);

            LoginRequestDto request = LoginRequestDto.builder()
                    .email("test@example.com")
                    .password("wrongpassword")
                    .build();

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode())
                                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
                    });
        }

        @Test
        @DisplayName("should throw AUTH_UNAUTHORIZED for deactivated user")
        void login_deactivatedUser_throwsException() {
            testUser.setActive(false);
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(testUser));

            LoginRequestDto request = LoginRequestDto.builder()
                    .email("test@example.com")
                    .password("password")
                    .build();

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode())
                                .isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
                    });
        }

        @Test
        @DisplayName("should throw when user has no password hash")
        void login_noPasswordHash_throwsException() {
            testUser.setPasswordHash(null);
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(testUser));

            LoginRequestDto request = LoginRequestDto.builder()
                    .email("test@example.com")
                    .password("password")
                    .build();

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode())
                                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
                    });
        }

        @Test
        @DisplayName("should persist refresh token on successful login")
        void login_success_persistsRefreshToken() {
            when(userRepository.findByEmail("test@example.com"))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("password", "$2a$10$hashedpassword"))
                    .thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                    .thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(any()))
                    .thenReturn("refresh-token");
            when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            LoginRequestDto request = LoginRequestDto.builder()
                    .email("test@example.com")
                    .password("password")
                    .deviceInfo("Chrome/Windows")
                    .build();

            authService.login(request);

            ArgumentCaptor<RefreshToken> captor =
                    ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            RefreshToken saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getToken()).isEqualTo("refresh-token");
            assertThat(saved.getDeviceInfo()).isEqualTo("Chrome/Windows");
            assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshTokenTests {

        @Test
        @DisplayName("should issue new access token with valid refresh token")
        void refreshToken_validToken_returnsNewAccessToken() {
            RefreshToken stored = RefreshToken.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .token("valid-refresh-token")
                    .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                    .build();

            when(refreshTokenRepository.findByToken("valid-refresh-token"))
                    .thenReturn(Optional.of(stored));
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                    .thenReturn("new-access-token");

            TokenRefreshDto request = TokenRefreshDto.builder()
                    .refreshToken("valid-refresh-token")
                    .build();

            LoginResponseDto response = authService.refreshToken(request);

            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("valid-refresh-token");
            assertThat(response.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should throw AUTH_TOKEN_EXPIRED for expired refresh token")
        void refreshToken_expiredToken_throwsException() {
            RefreshToken stored = RefreshToken.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .token("expired-token")
                    .expiresAt(Instant.now().minus(Duration.ofDays(1)))
                    .build();

            when(refreshTokenRepository.findByToken("expired-token"))
                    .thenReturn(Optional.of(stored));

            TokenRefreshDto request = TokenRefreshDto.builder()
                    .refreshToken("expired-token")
                    .build();

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode())
                                .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
                    });

            // Should delete the expired token
            verify(refreshTokenRepository).delete(stored);
        }

        @Test
        @DisplayName("should throw AUTH_TOKEN_EXPIRED when token not found in DB")
        void refreshToken_tokenNotFound_throwsException() {
            when(refreshTokenRepository.findByToken("nonexistent-token"))
                    .thenReturn(Optional.empty());

            TokenRefreshDto request = TokenRefreshDto.builder()
                    .refreshToken("nonexistent-token")
                    .build();

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .satisfies(ex -> {
                        BusinessRuleException bre = (BusinessRuleException) ex;
                        assertThat(bre.getErrorCode())
                                .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED);
                    });
        }
    }

    @Nested
    @DisplayName("logout")
    class LogoutTests {

        @Test
        @DisplayName("should delete refresh token on logout")
        void logout_validToken_deletesFromDb() {
            authService.logout("refresh-token-to-revoke");

            verify(refreshTokenRepository).deleteByToken("refresh-token-to-revoke");
        }
    }
}

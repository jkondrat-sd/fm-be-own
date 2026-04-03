package com.fm.be.modules.auth;

import com.fm.be.modules.auth.dto.LoginRequestDto;
import com.fm.be.modules.auth.dto.LoginResponseDto;
import com.fm.be.modules.auth.dto.OtpRequestDto;
import com.fm.be.modules.auth.dto.OtpVerifyDto;
import com.fm.be.modules.auth.dto.TokenRefreshDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Nested
    @DisplayName("POST /otp/request")
    class RequestOtpTests {

        @Test
        @DisplayName("delegates to AuthService.requestOtp and returns success message")
        void delegatesToService() {
            OtpRequestDto request = OtpRequestDto.builder().phone("+919876543210").build();

            Map<String, String> result = authController.requestOtp(request);

            verify(authService).requestOtp(request);
            assertThat(result).containsEntry("message", "OTP sent successfully");
        }
    }

    @Nested
    @DisplayName("POST /otp/verify")
    class VerifyOtpTests {

        @Test
        @DisplayName("delegates to AuthService.verifyOtp and returns login response")
        void delegatesToService() {
            OtpVerifyDto request = mock(OtpVerifyDto.class);
            LoginResponseDto expected = mock(LoginResponseDto.class);
            when(authService.verifyOtp(request)).thenReturn(expected);

            LoginResponseDto result = authController.verifyOtp(request);

            verify(authService).verifyOtp(request);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("POST /login")
    class LoginTests {

        @Test
        @DisplayName("delegates to AuthService.login and returns login response")
        void delegatesToService() {
            LoginRequestDto request = mock(LoginRequestDto.class);
            LoginResponseDto expected = mock(LoginResponseDto.class);
            when(authService.login(request)).thenReturn(expected);

            LoginResponseDto result = authController.login(request);

            verify(authService).login(request);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("POST /refresh")
    class RefreshTokenTests {

        @Test
        @DisplayName("delegates to AuthService.refreshToken and returns login response")
        void delegatesToService() {
            TokenRefreshDto request = mock(TokenRefreshDto.class);
            LoginResponseDto expected = mock(LoginResponseDto.class);
            when(authService.refreshToken(request)).thenReturn(expected);

            LoginResponseDto result = authController.refreshToken(request);

            verify(authService).refreshToken(request);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("POST /logout")
    class LogoutTests {

        @Test
        @DisplayName("delegates refresh token to AuthService.logout and returns 200 with message")
        void delegatesToService() {
            TokenRefreshDto request = TokenRefreshDto.builder().refreshToken("refresh-token-value").build();

            ResponseEntity<Map<String, String>> response = authController.logout(request);

            verify(authService).logout("refresh-token-value");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("message", "Logged out successfully");
        }
    }
}

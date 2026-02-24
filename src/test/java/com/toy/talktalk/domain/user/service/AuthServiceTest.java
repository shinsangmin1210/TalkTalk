package com.toy.talktalk.domain.user.service;

import com.toy.talktalk.domain.user.dto.LoginRequest;
import com.toy.talktalk.domain.user.dto.LoginResponse;
import com.toy.talktalk.domain.user.entity.User;
import com.toy.talktalk.domain.user.repository.UserRepository;
import com.toy.talktalk.global.exception.BusinessException;
import com.toy.talktalk.global.exception.ErrorCode;
import com.toy.talktalk.global.jwt.JwtProvider;
import com.toy.talktalk.global.jwt.JwtTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("로그인 성공 - JwtTokens 반환")
    void login_success() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604800000L);

        User user = buildUser(1L, "test@example.com");
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        JwtTokens tokens = new JwtTokens("accessToken", "refreshToken");

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);
        given(jwtProvider.generateTokens(user.getId())).willReturn(tokens);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        JwtTokens result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("accessToken");
        assertThat(result.refreshToken()).isEqualTo("refreshToken");
    }

    @Test
    @DisplayName("로그인 시 이메일 없으면 USER_NOT_FOUND 예외 발생")
    void login_userNotFound_throwsUserNotFound() {
        LoginRequest request = new LoginRequest("none@example.com", "password123");
        given(userRepository.findByEmail(request.email())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("로그인 시 비밀번호 불일치면 INVALID_PASSWORD 예외 발생")
    void login_wrongPassword_throwsInvalidPassword() {
        User user = buildUser(1L, "test@example.com");
        LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 새 Access Token 반환")
    void refresh_success() {
        String refreshToken = "validRefreshToken";
        Long userId = 1L;

        given(jwtProvider.extractUserId(refreshToken)).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:" + userId)).willReturn(refreshToken);
        given(jwtProvider.generateAccessToken(userId)).willReturn("newAccessToken");

        LoginResponse response = authService.refresh(refreshToken);

        assertThat(response.accessToken()).isEqualTo("newAccessToken");
    }

    @Test
    @DisplayName("Redis에 저장된 토큰과 다르면 INVALID_TOKEN 예외 발생")
    void refresh_tokenMismatch_throwsInvalidToken() {
        String refreshToken = "validRefreshToken";
        Long userId = 1L;

        given(jwtProvider.extractUserId(refreshToken)).willReturn(userId);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:" + userId)).willReturn("differentToken");

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("로그아웃 성공 - Redis 토큰 삭제")
    void logout_success() {
        String refreshToken = "validRefreshToken";
        Long userId = 1L;

        given(jwtProvider.extractUserId(refreshToken)).willReturn(userId);

        authService.logout(refreshToken);

        then(redisTemplate).should().delete("refresh:" + userId);
    }

    private User buildUser(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .password("encodedPassword")
                .nickname("닉네임")
                .role("ROLE_USER")
                .build();
    }
}

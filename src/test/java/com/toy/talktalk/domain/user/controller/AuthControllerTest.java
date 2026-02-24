package com.toy.talktalk.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.talktalk.domain.user.dto.LoginRequest;
import com.toy.talktalk.domain.user.dto.LoginResponse;
import com.toy.talktalk.domain.user.dto.SignupRequest;
import com.toy.talktalk.domain.user.service.AuthService;
import com.toy.talktalk.domain.user.service.UserService;
import com.toy.talktalk.global.exception.BusinessException;
import com.toy.talktalk.global.exception.ErrorCode;
import com.toy.talktalk.global.jwt.JwtProvider;
import com.toy.talktalk.global.jwt.JwtTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.toy.talktalk.global.config.SecurityConfig;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("회원가입 성공 - 201 반환")
    void signup_success() throws Exception {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "닉네임");
        willDoNothing().given(userService).signup(any());

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("회원가입 시 이메일 형식 오류 - 400 반환")
    void signup_invalidEmail_returns400() throws Exception {
        SignupRequest request = new SignupRequest("not-an-email", "password123", "닉네임");

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입 시 비밀번호 8자 미만 - 400 반환")
    void signup_shortPassword_returns400() throws Exception {
        SignupRequest request = new SignupRequest("test@example.com", "short", "닉네임");

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인 성공 - accessToken 반환 및 refreshToken 쿠키 설정")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        JwtTokens tokens = new JwtTokens("accessToken", "refreshToken");
        given(authService.login(any())).willReturn(tokens);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    @DisplayName("로그인 시 이메일 없으면 404 반환")
    void login_userNotFound_returns404() throws Exception {
        LoginRequest request = new LoginRequest("none@example.com", "password123");
        given(authService.login(any())).willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 새 accessToken 반환")
    void refresh_success() throws Exception {
        given(authService.refresh("validRefreshToken")).willReturn(LoginResponse.of("newAccessToken"));

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(new Cookie("refreshToken", "validRefreshToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("newAccessToken"));
    }

    @Test
    @DisplayName("refreshToken 쿠키 없이 재발급 요청 시 401 반환")
    void refresh_noCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 성공 - refreshToken 쿠키 만료")
    void logout_success() throws Exception {
        willDoNothing().given(authService).logout("validRefreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(new Cookie("refreshToken", "validRefreshToken")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0));
    }
}

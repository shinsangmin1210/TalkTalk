package com.toy.talktalk.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.talktalk.domain.user.dto.UpdateProfileRequest;
import com.toy.talktalk.domain.user.dto.UserProfileResponse;
import com.toy.talktalk.domain.user.service.UserService;
import com.toy.talktalk.global.exception.BusinessException;
import com.toy.talktalk.global.exception.ErrorCode;
import com.toy.talktalk.global.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.toy.talktalk.global.config.SecurityConfig;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtProvider jwtProvider;

    private static final Long USER_ID = 1L;

    private UsernamePasswordAuthenticationToken authToken() {
        return new UsernamePasswordAuthenticationToken(
                USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("내 프로필 조회 성공 - 200 반환")
    void getMyProfile_success() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                USER_ID, "test@example.com", "닉네임", null, LocalDateTime.now());
        given(userService.getUserProfile(USER_ID)).willReturn(response);

        mockMvc.perform(get("/api/users/me")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.nickname").value("닉네임"));
    }

    @Test
    @DisplayName("인증 없이 내 프로필 조회 시 401 반환")
    void getMyProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("프로필 수정 성공 - 200 반환")
    void updateMyProfile_success() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("새닉네임", null);
        UserProfileResponse response = new UserProfileResponse(
                USER_ID, "test@example.com", "새닉네임", null, LocalDateTime.now());
        given(userService.updateProfile(eq(USER_ID), any())).willReturn(response);

        mockMvc.perform(patch("/api/users/me")
                        .with(authentication(authToken()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임"));
    }

    @Test
    @DisplayName("존재하지 않는 userId로 프로필 수정 시 404 반환")
    void updateMyProfile_notFound_returns404() throws Exception {
        given(userService.updateProfile(eq(USER_ID), any()))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(patch("/api/users/me")
                        .with(authentication(authToken()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("닉네임", null))))
                .andExpect(status().isNotFound());
    }
}

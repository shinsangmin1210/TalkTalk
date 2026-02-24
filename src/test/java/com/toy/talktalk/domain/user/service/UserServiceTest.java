package com.toy.talktalk.domain.user.service;

import com.toy.talktalk.domain.user.dto.SignupRequest;
import com.toy.talktalk.domain.user.dto.UpdateProfileRequest;
import com.toy.talktalk.domain.user.dto.UserProfileResponse;
import com.toy.talktalk.domain.user.entity.User;
import com.toy.talktalk.domain.user.repository.UserRepository;
import com.toy.talktalk.global.exception.BusinessException;
import com.toy.talktalk.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        SignupRequest request = new SignupRequest("test@example.com", "password123", "닉네임");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");

        assertThatNoException().isThrownBy(() -> userService.signup(request));

        then(userRepository).should().save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 시 이메일 중복이면 EMAIL_ALREADY_EXISTS 예외 발생")
    void signup_duplicateEmail_throwsEmailAlreadyExists() {
        SignupRequest request = new SignupRequest("duplicate@example.com", "password123", "닉네임");
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("내 프로필 조회 성공")
    void getUserProfile_success() {
        User user = buildUser(1L, "test@example.com", "닉네임");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserProfileResponse response = userService.getUserProfile(1L);

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.nickname()).isEqualTo("닉네임");
    }

    @Test
    @DisplayName("존재하지 않는 userId로 프로필 조회 시 USER_NOT_FOUND 예외 발생")
    void getUserProfile_notFound_throwsUserNotFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("프로필 수정 성공")
    void updateProfile_success() {
        User user = buildUser(1L, "test@example.com", "기존닉네임");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest("새닉네임", null);
        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.nickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("존재하지 않는 userId로 프로필 수정 시 USER_NOT_FOUND 예외 발생")
    void updateProfile_notFound_throwsUserNotFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(999L, new UpdateProfileRequest("닉네임", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private User buildUser(Long id, String email, String nickname) {
        return User.builder()
                .id(id)
                .email(email)
                .password("encodedPassword")
                .nickname(nickname)
                .role("ROLE_USER")
                .build();
    }
}

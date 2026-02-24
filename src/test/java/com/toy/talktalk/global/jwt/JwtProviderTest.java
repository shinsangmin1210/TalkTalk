package com.toy.talktalk.global.jwt;

import com.toy.talktalk.global.exception.BusinessException;
import com.toy.talktalk.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-characters-long";
    private static final long ACCESS_TOKEN_EXPIRATION = 3600000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L;

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
    }

    @Test
    @DisplayName("토큰 생성 후 userId 추출 성공")
    void generateTokens_extractUserId() {
        Long userId = 1L;

        JwtTokens tokens = jwtProvider.generateTokens(userId);

        assertThat(jwtProvider.extractUserId(tokens.accessToken())).isEqualTo(userId);
        assertThat(jwtProvider.extractUserId(tokens.refreshToken())).isEqualTo(userId);
    }

    @Test
    @DisplayName("Access Token 단독 생성 및 검증 성공")
    void generateAccessToken_validateSuccess() {
        Long userId = 2L;

        String token = jwtProvider.generateAccessToken(userId);

        assertThatNoException().isThrownBy(() -> jwtProvider.validateToken(token));
        assertThat(jwtProvider.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("만료된 토큰 검증 시 EXPIRED_TOKEN 예외 발생")
    void validateToken_expired_throwsExpiredToken() throws InterruptedException {
        JwtProvider shortLivedProvider = new JwtProvider(SECRET, 1L, 1L);
        String token = shortLivedProvider.generateAccessToken(1L);
        Thread.sleep(10);

        assertThatThrownBy(() -> shortLivedProvider.validateToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("잘못된 형식의 토큰 검증 시 INVALID_TOKEN 예외 발생")
    void validateToken_invalid_throwsInvalidToken() {
        assertThatThrownBy(() -> jwtProvider.validateToken("this.is.not.a.valid.jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("빈 토큰 검증 시 INVALID_TOKEN 예외 발생")
    void validateToken_blank_throwsInvalidToken() {
        assertThatThrownBy(() -> jwtProvider.validateToken(""))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}

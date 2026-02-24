package com.toy.talktalk.domain.user.service;

import com.toy.talktalk.domain.user.dto.LoginRequest;
import com.toy.talktalk.domain.user.dto.LoginResponse;
import com.toy.talktalk.domain.user.entity.User;
import com.toy.talktalk.domain.user.repository.UserRepository;
import com.toy.talktalk.global.exception.BusinessException;
import com.toy.talktalk.global.exception.ErrorCode;
import com.toy.talktalk.global.jwt.JwtProvider;
import com.toy.talktalk.global.jwt.JwtTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public JwtTokens login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        JwtTokens tokens = jwtProvider.generateTokens(user.getId());
        saveRefreshToken(user.getId(), tokens.refreshToken());
        return tokens;
    }

    public LoginResponse refresh(String refreshToken) {
        jwtProvider.validateToken(refreshToken);
        Long userId = jwtProvider.extractUserId(refreshToken);

        String storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + userId);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = jwtProvider.generateAccessToken(userId);
        return LoginResponse.of(newAccessToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        jwtProvider.validateToken(refreshToken);
        Long userId = jwtProvider.extractUserId(refreshToken);
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);
    }

    private void saveRefreshToken(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + userId,
                refreshToken,
                refreshTokenExpiration,
                TimeUnit.MILLISECONDS
        );
    }
}

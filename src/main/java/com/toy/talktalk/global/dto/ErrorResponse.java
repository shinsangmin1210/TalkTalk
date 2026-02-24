package com.toy.talktalk.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL) // null 필드는 응답에서 제외
public record ErrorResponse(
        int status,           // HTTP 상태 코드
        String code,          // 애플리케이션 에러 코드
        String message,       // 에러 메시지
        List<FieldError> errors, // 필드 유효성 검사 에러 목록 (validation 실패 시)
        LocalDateTime timestamp
) {

    // 일반 에러 응답 생성
    public static ErrorResponse of(HttpStatus status, String code, String message) {
        return new ErrorResponse(status.value(), code, message, null, LocalDateTime.now());
    }

    // 필드 유효성 검사 에러 응답 생성
    public static ErrorResponse of(HttpStatus status, String code, String message, List<FieldError> errors) {
        return new ErrorResponse(status.value(), code, message, errors, LocalDateTime.now());
    }

    // 필드별 유효성 검사 에러 정보
    public record FieldError(
            String field,       // 에러가 발생한 필드명
            Object value,       // 입력된 값
            String reason       // 에러 사유
    ) {
        public static FieldError of(String field, Object value, String reason) {
            return new FieldError(field, value, reason);
        }
    }
}

package com.team04.mopl.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;

// 커스텀 예외 기본 클래스
// 도메인별 예외 클래스는 해당 클래스를 상속해서 사용
@Getter
public abstract class MoplException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Map<String, Object> details = new LinkedHashMap<>();

  // 에러코드만 포함하는 예외
  protected MoplException(ErrorCode errorCode) {
    super(validateErrorCode(errorCode).getMessage());
    this.errorCode = errorCode;
  }

  // 에러코드와 기존 예외를 원인으로 포함하는 예외
  protected MoplException(ErrorCode errorCode, Throwable cause) {
    super(validateErrorCode(errorCode).getMessage(), cause);
    this.errorCode = errorCode;
  }

  // 응답에 포함할 부가 정보를 추가
  public MoplException addDetail(String key, Object value) {
    details.put(
        Objects.requireNonNull(key, "detail key는 null일 수 없습니다."),
        Objects.requireNonNull(value, "detail value는 null일 수 없습니다.")
    );

    return this;
  }

  // 외부에서 details를 직접 수정하지 못하도록 읽기 전용 Map 반환
  public Map<String, Object> getDetails() {
    return Collections.unmodifiableMap(
        new LinkedHashMap<>(details)
    );
  }

  // ErrorCode 구현체의 필수 값 검증
  // 예외 처리 중 getHttpStatus(), getCode(), getMessage()에서 2차 NPE가 터지는 것을 방지
  private static ErrorCode validateErrorCode(ErrorCode errorCode) {
    ErrorCode validated = Objects.requireNonNull(
        errorCode,
        "errorCode는 null일 수 없습니다."
    );

    Objects.requireNonNull(
        validated.getHttpStatus(),
        "errorCode.httpStatus는 null일 수 없습니다."
    );

    if (validated.getCode() == null || validated.getCode().isBlank()) {
      throw new IllegalArgumentException("errorCode.code는 null/blank일 수 없습니다.");
    }

    if (validated.getMessage() == null || validated.getMessage().isBlank()) {
      throw new IllegalArgumentException("errorCode.message는 null/blank일 수 없습니다.");
    }

    return validated;
  }
}

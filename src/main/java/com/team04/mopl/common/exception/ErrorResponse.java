package com.team04.mopl.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

// API 에러 응답 공통 포맷
@Getter
@Schema(description = "에러 응답")
public class ErrorResponse {

  @Schema(description = "예외 이름")
  private final String exceptionName;
  @Schema(description = "오류 메시지")
  private final String message;
  @Schema(description = "오류 부가 정보")
  private final Map<String, String> details;

  private ErrorResponse(
      String exceptionName,
      String message,
      Map<String, String> details
  ) {
    this.exceptionName = exceptionName;
    this.message = message;
    this.details = details;
  }

  // 커스텀 예외 응답 생성
  public static ErrorResponse from(MoplException exception) {
    return new ErrorResponse(
        exception.getClass().getSimpleName(),
        exception.getMessage(),
        convertDetails(exception.getDetails())
    );
  }

  // Spring MVC 프레임워크 예외 응답 생성
  public static ErrorResponse of(
      Exception exception,
      String message,
      Map<String, ?> details
  ) {
    return new ErrorResponse(
        exception.getClass().getSimpleName(),
        message,
        convertDetails(details)
    );
  }

  // 예상하지 못한 서버 오류 응답 생성
  // 실제 예외 클래스명과 메시지는 외부에 노출하지 않음
  public static ErrorResponse internalServerError() {
    return new ErrorResponse(
        "InternalServerException",
        "서버 내부 오류가 발생했습니다.",
        Map.of()
    );
  }

  // Swagger 명세에 맞춰 details 값을 모두 문자열로 변환
  private static Map<String, String> convertDetails(
      Map<String, ?> source
  ) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }

    Map<String, String> converted = new LinkedHashMap<>();

    source.forEach((key, value) ->
        converted.put(key, String.valueOf(value))
    );

    return Collections.unmodifiableMap(converted);
  }
}

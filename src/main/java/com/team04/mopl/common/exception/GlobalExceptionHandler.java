package com.team04.mopl.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

// 공통 에러 응답으로 변환하는 핸들러
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	// 서비스에서 직접 발생시킨 커스텀 예외 처리
	@ExceptionHandler(MoplException.class)
	public ResponseEntity<ErrorResponse> handleMoplException(
		MoplException exception
	) {
		ErrorCode errorCode = exception.getErrorCode();
		HttpStatus status = errorCode.getHttpStatus();

		if (status.is5xxServerError()) {
			log.error(
				"[MoplException] code={}, exceptionName={}, message={}, details={}",
				errorCode.getCode(),
				exception.getClass().getSimpleName(),
				exception.getMessage(),
				exception.getDetails(),
				exception
			);
		} else {
			log.warn("[MoplException] code={}, exceptionName={}, message={}, details={}",
				errorCode.getCode(),
				exception.getClass().getSimpleName(),
				exception.getMessage(),
				exception.getDetails(),
				exception
			);
		}

		return ResponseEntity
			.status(status)
			.body(ErrorResponse.from(exception));
	}

	// @RequestBody 또는 @RequestPart DTO의 @Valid 실패 처리
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(
		MethodArgumentNotValidException exception
	) {
		Map<String, String> details = new LinkedHashMap<>();

		// 필드 단위 검증 오류 처리
		exception.getBindingResult()
			.getFieldErrors()
			.forEach(error ->
				mergeDetail(
					details,
					error.getField(),
					defaultMessage(error.getDefaultMessage())
				)
			);

		// 객체 전체를 대상으로 하는 검증 오류 처리
		exception.getBindingResult()
			.getGlobalErrors()
			.forEach(error ->
				mergeDetail(
					details,
					"_global",
					defaultMessage(error.getDefaultMessage())
				)
			);

		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(
				exception,
				"요청 데이터 유효성 검사에 실패했습니다.",
				details
			));
	}

	// DTO 객체 생성 시 컴팩트 생성자에서 발생한 예외 처리
	@ExceptionHandler(BeanInstantiationException.class)
	public ResponseEntity<ErrorResponse> handleBeanInstantiationException(
		BeanInstantiationException exception
	) {
		Map<String, String> details = new LinkedHashMap<>();
		// 원본 예외 메시지 추출
		Throwable cause = exception.getCause();

		log.warn("[BeanInstantiationException] cause={}",
			cause != null ? cause.getClass().getSimpleName() : "unknown", exception);

		// 검증 목적으로 던진 예외인 경우에만 상세 정보 추가
		if (cause instanceof IllegalArgumentException || cause instanceof MoplException) {
			mergeDetail(
				details,
				"_global",
				defaultMessage(cause.getMessage())
			);

			return ResponseEntity
				.badRequest()
				.body(ErrorResponse.of(
					exception,
					"요청 파라미터 유효성 검사에 실패했습니다.",
					details
				));
		}

		// 검증 실패가 아닌 서버 내부 문제인 경우 500 응답
		return ResponseEntity
			.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ErrorResponse.internalServerError());
	}

	// @RequestParam, @PathVariable 등에 직접 적용된 Validation 실패 처리
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolationException(
		ConstraintViolationException exception
	) {
		Map<String, String> details = new LinkedHashMap<>();

		exception.getConstraintViolations()
			.forEach(violation ->
				mergeDetail(
					details,
					extractFieldName(
						violation.getPropertyPath().toString()
					),
					defaultMessage(violation.getMessage())
				)
			);

		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(
				exception,
				"요청 파라미터 유효성 검사에 실패했습니다.",
				details
			));
	}

	// UUID, enum, 숫자 등 요청 파라미터의 타입 변환 실패 처리
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatchException(
		MethodArgumentTypeMismatchException exception
	) {
		Map<String, String> details = new LinkedHashMap<>();

		details.put(
			"parameter",
			exception.getName()
		);

		if (exception.getRequiredType() != null) {
			details.put(
				"requiredType",
				exception.getRequiredType().getSimpleName()
			);
		}

		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(
				exception,
				"요청 파라미터 형식이 올바르지 않습니다.",
				details
			));
	}

	// 필수 @RequestParam 값이 누락된 경우 처리
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingRequestParameterException(
		MissingServletRequestParameterException exception
	) {
		Map<String, String> details = Map.of(
			"parameter",
			exception.getParameterName(),
			"parameterType",
			exception.getParameterType()
		);

		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(
				exception,
				"필수 요청 파라미터가 누락되었습니다.",
				details
			));
	}

	// JSON 문법 오류, enum 변환 실패, 잘못된 필드 타입 처리
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
		HttpMessageNotReadableException exception
	) {
		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(
				exception,
				"요청 본문 형식이 올바르지 않습니다.",
				Map.of()
			));
	}

	// AccessDeniedException
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDeniedException(
		AccessDeniedException exception
	) {
		AuthException authException = new AuthException(AuthErrorCode.AUTH_ACCESS_DENIED, exception);

		log.warn(
			"[AccessDenied] 접근 권한 없음: exceptionName={}, message={}",
			exception.getClass().getSimpleName(),
			exception.getMessage()
		);

		return ResponseEntity
			.status(AuthErrorCode.AUTH_ACCESS_DENIED.getHttpStatus())
			.body(ErrorResponse.from(authException));
	}

	// 위에서 처리하지 못한 모든 예외의 마지막 방어선
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(
		Exception exception
	) {
		// 실제 예외 클래스와 stack trace는 서버 로그에만 기록
		log.error(
			"[Exception] 예상하지 못한 오류 발생: exceptionName={}",
			exception.getClass().getSimpleName(),
			exception
		);

		// 실제 예외 정보는 클라이언트에게 노출하지 않음
		return ResponseEntity
			.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ErrorResponse.internalServerError());
	}

	// 동일 필드에서 여러 검증 오류가 발생하면 문자열로 합침
	// Swagger details의 value가 string이므로 List를 사용하지 않음
	private void mergeDetail(
		Map<String, String> details,
		String key,
		String message
	) {
		details.merge(
			key,
			message,
			(previous, current) -> previous + ", " + current
		);
	}

	// ConstraintViolation의 전체 경로에서 마지막 필드명만 추출
	private String extractFieldName(String propertyPath) {
		if (propertyPath == null || propertyPath.isBlank()) {
			return "_parameter";
		}

		int lastDotIndex = propertyPath.lastIndexOf('.');

		if (lastDotIndex < 0) {
			return propertyPath;
		}

		return propertyPath.substring(lastDotIndex + 1);
	}

	// Validation 메시지가 없으면 기본 메시지 사용
	private String defaultMessage(String message) {
		if (message == null || message.isBlank()) {
			return "유효하지 않은 값입니다.";
		}

		return message;
	}
}
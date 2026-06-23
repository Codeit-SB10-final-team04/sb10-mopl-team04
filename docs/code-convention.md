# 백엔드 코드 컨벤션 (Backend Code Convention)

> 💡 이 문서는 팀원 모두가 일관된 방식으로 협업하기 위한 규칙을 정의합니다.  
> CodeRabbit이 코드 리뷰 시 이 문서를 기준으로 검사합니다. 모든 팀원은 작업 전 반드시 숙지해주세요.

---

## 목차

1. [패키지 및 디렉토리 구조](#1-패키지-및-디렉토리-구조)
2. [명명 규칙](#2-명명-규칙)
3. [예외 처리 및 응답 포맷](#3-예외-처리-및-응답-포맷)
4. [데이터베이스 및 JPA 활용 규칙](#4-데이터베이스-및-jpa-활용-규칙)
5. [테스트 코드 작성 규칙](#5-테스트-코드-작성-규칙)
6. [기타 개발 규칙](#6-기타-개발-규칙)

---

## 1. 패키지 및 디렉토리 구조

> 도메인 단위로 패키지를 구성합니다. 각 도메인 안에 `controller` / `service` / `dto`를 두는 구조로, 응집도를 높이고 관련 코드를 한 곳에서 찾을 수 있도록 합니다.

```text
Member
    ⎿ controller
    ⎿ service
    ⎿ dto
Order
    ⎿ controller
        ⎿ OrderController.java
    ⎿ service
    ⎿ dto
```

### 레이어드 아키텍처 규칙

- `Controller`는 `Service`만 호출해야 하며, `Repository`에 직접 접근하지 않는다.
- `Entity`(도메인) 객체는 데이터베이스 레이어에 종속되지 않도록 비즈니스 로직에 집중한다.
- `Controller`는 인터페이스로 분리한다 → `도메인ControllerDocs` (가독성 목적)
- `Service` 인터페이스 분리는 하지 않는다.

### DTO 규칙

- 요청(Request)과 응답(Response)은 반드시 독립적인 DTO를 사용하며, `Entity`를 API 외부에 직접 노출하지 않는다.
- `request` / `response` 디렉토리로 분리하며, `Dto` 접미사를 사용한다.

---

## 2. 명명 규칙

> CodeRabbit이 클래스, 메서드, 변수명이 직관적이고 일관된지 검사하는 기준입니다.

### 기본 네이밍

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 / 인터페이스 | PascalCase | `UserService`, `OrderController` |
| 메서드 / 변수 | camelCase | `findById`, `totalAmount` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRIES` |
| 테스트 메서드 | 메소드명_기대결과_테스트상태 | `isAdult_False_AgeLessThan18` |

### 메서드 접두사 약속

| 접두사 | 의미 | 반환 타입 |
|--------|------|-----------|
| `find...` | 단건 조회 | Entity 또는 DTO |
| `findAll...` / `search...` | 목록 조회 | List 또는 Page |
| `exists...` | 존재 여부 확인 | 반드시 `boolean` |
| `get...` | 없으면 예외를 던진다 (팀 내 약속) | Entity |
| `validate...` | 유효성 검증, 위반 시 예외 던짐 | `void` |

### 메서드 네이밍 예시

```java
// 외부 비즈니스 메서드
@Transactional(readOnly = true)
public UserResponse getUserProfile(UUID userId) {
    UserEntity user = getUserEntityOrThrow(userId);
    return UserResponse.from(user);
}

// 내부 헬퍼 - 없으면 예외
private UserEntity getUserEntityOrThrow(UUID userId) {
    return userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
}

// 유효성 검증 - 중복 검사
private void validateDuplicateReview(UUID bookId, UUID userId) {
    if (reviewRepository.existsByBookIdAndUserIdAndStatus(bookId, userId, ReviewStatus.ACTIVE)) {
        throw new DuplicateReviewException(bookId, userId);
    }
}

// 유효성 검증 - 권한 확인
private void validateOwner(Review targetReview, User requestUser) {
    boolean isOwner = targetReview.getUser().getId().equals(requestUser.getId());
    if (!isOwner) {
        throw new ReviewAuthorMismatchException(targetReview.getUser().getId(), requestUser.getId());
    }
}
```

---

## 3. 예외 처리 및 응답 포맷

> 안정성(Null 및 에러 처리)을 꼼꼼하게 판단하기 위해 가장 중요한 항목입니다.  
> CodeRabbit이 가장 날카롭게 잡아내는 구간입니다.

### 예외 처리 규칙

- 표준 예외(`IllegalArgumentException` 등)를 우선 고려하되, 비즈니스 예외는 커스텀 예외(`CustomException`)로 통일하여 던진다.
- 모든 에러 응답은 공통 에러 객체 포맷(`ErrorResponse`)으로 감싸서 반환한다.
- 메서드 반환 값으로 `null`을 절대 반환하지 않는다. 값이 없을 가능성이 있다면 반드시 `Optional`을 사용한다.

### Custom Error 구조

ErrorCode 네이밍: `CM00`, `US00` 형식 (도메인 약어 + 번호)

```java
// 1. ErrorCode 인터페이스
public interface ErrorCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
}

// 2. 도메인별 ErrorCode enum
public enum CommentErrorCode implements ErrorCode {
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CM01", "댓글을 찾을 수 없습니다."),
    COMMENT_NOT_OWNED_BY_USER(HttpStatus.FORBIDDEN, "CM02", "본인의 댓글이 아닙니다."),
    COMMENT_LIKE_ALREADY_EXISTS(HttpStatus.CONFLICT, "CM03", "이미 좋아요를 눌렀습니다."),
    COMMENT_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "CM04", "좋아요를 누르지 않았습니다."),
    COMMENT_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "CM05", "이미 삭제한 댓글입니다.");
}

// 3. 추상 기반 예외
@Getter
public abstract class MonewException extends RuntimeException {
    private final Instant timestamp;
    private final ErrorCode errorCode;
    private final Map<String, Object> details;
}

// 4. 도메인별 Exception
public class CommentException extends MonewException {
    public CommentException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode, details);
    }
    public CommentException(ErrorCode errorCode, UUID id) {
        super(errorCode, id);
    }
}
```

---

## 4. 데이터베이스 및 JPA 활용 규칙

> 백엔드 성능과 직결되는 쿼리 및 매핑 컨벤션입니다.

### 연관 관계 설정

- JPA 사용 시 `@ManyToOne` 등은 반드시 지연 로딩(`FetchType.LAZY`)으로 설정한다. **(EAGER 금지)**

### 트랜잭션 관리

- 단순 조회 메서드에는 `@Transactional(readOnly = true)`를 명시하여 성능을 최적화한다.
- 데이터 쓰기/수정이 일어나는 메서드에만 `@Transactional`을 부여한다.

### BaseEntity

- `JPAAuditingConfig` 설정을 사용한다.

### 로깅

- `Controller`에는 로그를 남기지 않는다.
- 비즈니스 로직 처리 중 상태 추적이 필요한 경우 `Service`에 로그를 남긴다.
- 배치(Batch), 스케줄러(Scheduler) 등 백그라운드 작업은 실행 흐름 파악을 위해 단계별로 로그를 반드시 남긴다.

---

## 5. 테스트 코드 작성 규칙

> CodeRabbit에게 "어떤 테스트가 누락되었는지 지적해 달라"고 요구하기 위한 명시적 기준입니다.

### 작성 원칙

- 기능 개발 시 테스트 코드를 함께 PR에 첨부한다.
- 최소 작업 단위는 테스트 코드까지 포함되어야 한다.
- 각 테스트 메서드는 독립적으로 실행되어야 하며, 다른 테스트에 영향을 주면 안 된다.

### 명명 규칙

테스트 메서드명은 `메소드명_기대결과_테스트상태` 구조로 작성하거나, `@DisplayName`을 통해 한글로 명확히 의도를 작성한다.

```java
// 방법 1: 메서드명 규칙
isAdult_False_AgeLessThan18

// 방법 2: @DisplayName 사용 (권장)
@Test
@DisplayName("존재하지 않는 회원 ID로 조회 시 예외가 발생한다")
void getUserEntityOrThrow_throwsException_whenUserNotFound() { ... }
```

---

## 6. 기타 개발 규칙

> 팀 내에서 합의한 개발 규칙들입니다. 애매한 케이스는 PR 본문에 이유를 명시해주세요.

### 코드 스타일

- Checkstyle: **네이버 스타일 가이드** 기준
- SpotBugs: 정적 분석으로 잠재적 버그 탐지

### 주석

- 주석은 최대한 자세하게 작성한다.
- `main` 브랜치 병합 후에도 주석을 삭제하지 않는다.
- 의존성 있는 엔티티가 미구현 상태라면 주석 또는 `// TODO` 처리한다.

### 환경 변수

- `.env` 파일은 커밋을 절대 금지한다.
- 시크릿은 팀 노션 비공개 페이지에서 공유한다.
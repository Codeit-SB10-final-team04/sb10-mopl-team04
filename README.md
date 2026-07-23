<div align="center">

# 🎬 모두의 플리 (MOPL)
<img width="600" height="400" alt="favicon" src="https://github.com/user-attachments/assets/002b0441-7f8a-4c15-a0fc-8b7f0d0413f4" />


### Global Content Curation & Rating Platform

대규모 트래픽을 고려한 글로벌 콘텐츠 평가 및 큐레이션 플랫폼

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7.2-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![Apache Kafka](https://img.shields.io/badge/Kafka-Confluent_Cloud-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)](https://www.confluent.io/)
[![AWS](https://img.shields.io/badge/AWS-ECS_Fargate-FF9900?style=for-the-badge&logo=amazonecs&logoColor=white)](https://aws.amazon.com/ecs/)

[![CI](https://img.shields.io/badge/CI-GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)](#cicd-pipeline)
[![Coverage](https://img.shields.io/badge/Coverage-85.2%25-brightgreen?style=flat-square)](#test-coverage)
[![Tests](https://img.shields.io/badge/Tests-841_passed-brightgreen?style=flat-square)](#test-coverage)
[![JaCoCo Gate](https://img.shields.io/badge/JaCoCo_Gate-80%25-blue?style=flat-square)](#code-quality)


<br/>

**플레이리스트 기반 큐레이션** · **실시간 같이보기(Watching Session)** · **이벤트 기반 아키텍처**

[API 문서](#api-endpoints) · [아키텍처](#system-architecture) · [성능 벤치마크](#performance-benchmarks) · [시작하기](#getting-started)

</div>

---

<br/>

## 📑 Table of Contents

- [About](#about)
- [System Architecture](#system-architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Endpoints](#api-endpoints)
- [Performance Benchmarks](#performance-benchmarks)
- [Test Coverage](#test-coverage)
- [Code Quality](#code-quality)
- [CI/CD Pipeline](#cicd-pipeline)
- [Monitoring](#monitoring)
- [Team](#team)


<br/>

## About

**모두의 플리(MOPL)** 는 영화, TV 시리즈, 스포츠 등 글로벌 콘텐츠를 평가하고 플레이리스트로 큐레이션하는 플랫폼입니다.

### Why MOPL?

- 🎯 **플레이리스트 큐레이션** — 단순 평점을 넘어 사용자가 직접 콘텐츠를 구성하고 공유
- 👥 **실시간 같이보기** — WebSocket/STOMP 기반 Watching Session으로 실시간 채팅과 함께 콘텐츠 감상
- ⚡ **이벤트 기반 설계** — Spring Event → Kafka → SSE로 도메인 간 느슨한 결합과 실시간 알림
- 🌍 **글로벌 데이터** — TMDB/SportsDB API를 Spring Batch로 자동 수집 및 동기화

### Key Numbers

| 항목 | 수치 |
|------|------|
| REST API Endpoints | 42개 |
| JPA Entities | 23개 |
| Kafka Topics | 6개 |
| Batch Jobs | 4개 |
| Custom Metrics | 40+ |
| Test Coverage | 85.2% (841 tests) |

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## System Architecture

</div>
<img width="1589" height="1770" alt="mopl-architecture" src="https://github.com/user-attachments/assets/66542455-aa8e-4ee4-adee-21bcc9ee1a23" />

### Event Flow

```
Domain Event → @TransactionalEventListener → Kafka Producer
    → Kafka Consumer → Notification → Redis Pub/Sub → SSE (Client)
```

### Infrastructure Highlights

| 구성 요소 | 설명 |
|-----------|------|
| **Load Balancing** | Nginx Round Robin (2대 App Server) |
| **SSL/TLS** | Let's Encrypt 인증서, HTTP→HTTPS 리다이렉트 |
| **WebSocket** | `/ws/*` 경로 Upgrade 헤더 프록시, 3600s 타임아웃 |
| **SSE** | `/api/sse` 경로 버퍼링 OFF, 캐시 OFF |
| **Container** | Docker 멀티스테이지 빌드 (amazoncorretto:17 → alpine), non-root user |
| **Orchestration** | AWS ECS Fargate, Rolling Update 배포 |

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## Tech Stack

<table>
  <tr>
    <th align="center">Category</th>
    <th align="center">Technology</th>
  </tr>
  <tr>
    <td><b>Framework</b></td>
    <td>
      <img src="https://img.shields.io/badge/Spring_Boot-3.5.15-6DB33F?style=flat-square&logo=springboot&logoColor=white"/>
      <img src="https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white"/>
      <img src="https://img.shields.io/badge/Spring_Batch-6DB33F?style=flat-square&logo=spring&logoColor=white"/>
    </td>
  </tr>
  <tr>
    <td><b>Database</b></td>
    <td>
      <img src="https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square&logo=postgresql&logoColor=white"/>
      <img src="https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white"/>
      <img src="https://img.shields.io/badge/QueryDSL-5.1.0-0769AD?style=flat-square"/>
    </td>
  </tr>
  <tr>
    <td><b>Cache</b></td>
    <td>
      <img src="https://img.shields.io/badge/Redis-7.2-DC382D?style=flat-square&logo=redis&logoColor=white"/>
      <img src="https://img.shields.io/badge/Caffeine-Local_Cache-6DB33F?style=flat-square"/>
      <img src="https://img.shields.io/badge/Redisson-3.37.0-DC382D?style=flat-square"/>
    </td>
  </tr>
  <tr>
    <td><b>Messaging</b></td>
    <td>
      <img src="https://img.shields.io/badge/Apache_Kafka-231F20?style=flat-square&logo=apachekafka&logoColor=white"/>
      <img src="https://img.shields.io/badge/Redis_Pub/Sub-DC382D?style=flat-square&logo=redis&logoColor=white"/>
    </td>
  </tr>
  <tr>
    <td><b>Real-time</b></td>
    <td>
      <img src="https://img.shields.io/badge/WebSocket/STOMP-010101?style=flat-square"/>
      <img src="https://img.shields.io/badge/SSE-Server_Sent_Events-010101?style=flat-square"/>
    </td>
  </tr>
  <tr>
    <td><b>Auth</b></td>
    <td>
      <img src="https://img.shields.io/badge/JWT-HS256-000000?style=flat-square&logo=jsonwebtokens&logoColor=white"/>
      <img src="https://img.shields.io/badge/OAuth2-Google-4285F4?style=flat-square&logo=google&logoColor=white"/>
      <img src="https://img.shields.io/badge/OAuth2-Kakao-FFCD00?style=flat-square&logo=kakao&logoColor=black"/>
    </td>
  </tr>
  <tr>
    <td><b>Infra</b></td>
    <td>
      <img src="https://img.shields.io/badge/AWS_ECS-Fargate-FF9900?style=flat-square&logo=amazonecs&logoColor=white"/>
      <img src="https://img.shields.io/badge/AWS_ECR-FF9900?style=flat-square&logo=amazon&logoColor=white"/>
      <img src="https://img.shields.io/badge/AWS_S3-569A31?style=flat-square&logo=amazons3&logoColor=white"/>
      <img src="https://img.shields.io/badge/Nginx-009639?style=flat-square&logo=nginx&logoColor=white"/>
      <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white"/>
    </td>
  </tr>
  <tr>
    <td><b>CI/CD</b></td>
    <td>
      <img src="https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white"/>
      <img src="https://img.shields.io/badge/JaCoCo-80%25_Gate-brightgreen?style=flat-square"/>
      <img src="https://img.shields.io/badge/Codecov-F01F7A?style=flat-square&logo=codecov&logoColor=white"/>
    </td>
  </tr>
  <tr>
    <td><b>Monitoring</b></td>
    <td>
      <img src="https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white"/>
      <img src="https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white"/>
      <img src="https://img.shields.io/badge/Micrometer-40+_Metrics-4DB33D?style=flat-square"/>
    </td>
  </tr>
  <tr>
    <td><b>Testing</b></td>
    <td>
      <img src="https://img.shields.io/badge/JUnit5-25A162?style=flat-square&logo=junit5&logoColor=white"/>
      <img src="https://img.shields.io/badge/TestContainers-PostgreSQL_|_Kafka-2496ED?style=flat-square&logo=docker&logoColor=white"/>
      <img src="https://img.shields.io/badge/k6-Load_Test-7D64FF?style=flat-square&logo=k6&logoColor=white"/>
    </td>
  </tr>
</table>

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## Project Structure

```
mopl/
├── src/main/java/com/team04/mopl/
│   ├── auth/                # JWT, OAuth2, 세션 관리
│   ├── user/                # 사용자 관리, 프로필
│   ├── content/             # 콘텐츠 CRUD + Batch (TMDB, SportsDB)
│   ├── review/              # 리뷰 및 평점
│   ├── playlist/            # 플레이리스트 큐레이션
│   ├── follow/              # 팔로우/팔로잉
│   ├── conversation/        # 대화방 + 실시간 채팅
│   ├── directmessage/       # 1:1 다이렉트 메시지
│   ├── notification/        # Kafka 기반 알림 (6 Topics)
│   ├── watching/            # 실시간 같이보기 (WebSocket/Redis)
│   ├── sse/                 # Server-Sent Events
│   ├── config/              # Security, WebSocket, Redis, Cache, S3
│   └── common/              # 공통 유틸, 예외, Redis 저장소
├── nginx/                   # Nginx 설정 및 Dockerfile
├── k6/                      # 부하 테스트 스크립트 및 결과
├── docker-compose.yml       # 로컬 개발 환경
├── Dockerfile               # 멀티스테이지 빌드
└── .github/workflows/       # CI/CD 파이프라인 (3개)
```

**도메인별 계층 구조** (각 도메인 공통)
```
domain/
├── controller/          # REST API + ControllerDocs (Swagger)
├── service/             # 비즈니스 로직
├── repository/          # JPA Repository + QueryDSL
├── entity/              # JPA Entity
├── dto/                 # Request/Response DTO
├── mapper/              # MapStruct 매퍼
└── event/               # 도메인 이벤트 (Publisher/Listener)
```

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## Getting Started

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Gradle 8.x

### Installation

```bash
# 1. 저장소 클론
git clone https://github.com/Codeit-SB10-final-team04/sb10-mopl-team04.git
cd sb10-mopl-team04

# 2. 환경 변수 설정
cp .env.example .env
# .env 파일에 필요한 값 입력 (DB, Redis, Kafka, JWT Secret 등)

# 3. Docker Compose로 인프라 실행
docker compose up -d postgres redis broker

# 4. 애플리케이션 빌드 및 실행
./gradlew clean build
./gradlew bootRun
```

### Multi-Server Testing

```bash
# 2대 서버 + Nginx 로드밸런싱 테스트
docker compose --profile multi up -d
```

### Configuration

| 환경 변수 | 설명 | 기본값 |
|-----------|------|--------|
| `DB_URL` | PostgreSQL 접속 URL | `jdbc:postgresql://localhost:5433/mopl` |
| `DB_USERNAME` | DB 사용자명 | `mopl` |
| `REDIS_HOST` | Redis 호스트 | `localhost` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 브로커 주소 | `localhost:9092` |
| `JWT_SECRET_KEY` | JWT 서명 키 | — |
| `TMDB_API_KEY` | TMDB API 키 | — |

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## API Endpoints

> 📄 전체 API 문서: 서버 실행 후 `/swagger-ui.html` 에서 확인


### REST APIs (42 Endpoints)

| Domain | Endpoints | Description |
|--------|-----------|-------------|
| **Auth** | `POST /api/auth/*` | 회원가입, 로그인, OAuth2, 토큰 갱신 |
| **User** | `GET/PUT /api/users/*` | 프로필 조회/수정, 프로필 이미지 업로드 |
| **Content** | `GET /api/contents/*` | 콘텐츠 목록/상세 조회, 검색 |
| **Review** | `POST/GET/PUT/DELETE /api/contents/*/reviews/*` | 리뷰 CRUD, 평점 |
| **Playlist** | `POST/GET/PUT/DELETE /api/playlists/*` | 플레이리스트 CRUD, 구독 |
| **Follow** | `POST/DELETE/GET /api/follows/*` | 팔로우/언팔로우, 목록 조회 |
| **Conversation** | `POST/GET /api/conversations/*` | 대화방 생성/목록/메시지 조회 |
| **DirectMessage** | `POST/GET /api/direct-messages/*` | DM 전송/조회 |
| **Notification** | `GET/DELETE /api/notifications/*` | 알림 목록/읽음 처리 |
| **Watching** | `POST/GET /api/watching-sessions/*` | 같이보기 세션 생성/조회 |


### WebSocket (STOMP)

| Destination | Description |
|-------------|-------------|
| `/ws/connect` | WebSocket 연결 엔드포인트 |
| `/topic/conversations/{id}` | 대화방 실시간 메시지 |
| `/topic/watching-sessions/{id}` | 같이보기 세션 채팅 |
| `/topic/watching-sessions/{id}/viewers` | 시청자 목록 실시간 업데이트 |

### SSE

| Endpoint | Description |
|----------|-------------|
| `GET /api/sse` | 실시간 알림·DM 구독 및 미수신 이벤트 복구 |

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## Performance Benchmarks

> 📊 k6 부하 테스트 (VU 25 → 50 → 70 → 70 유지 → 0, 총 5분)

### Load Test Results (9 APIs)

| API | p95 | avg | max | req/s | 에러율 |
|-----|-----|-----|-----|-------|--------|
| content-list | 36.58ms | 16.15ms | 342.99ms | 140.2 | 0% |
| content-detail | 23.46ms | 11.21ms | 289.41ms | 148.7 | 0% |
| review-list | 64.68ms | 24.25ms | 597.96ms | 118.0 | 0% |
| playlist-list | 31.22ms | 14.87ms | 312.56ms | 135.4 | 0% |
| search | 45.12ms | 19.33ms | 401.23ms | 128.6 | 0% |

> 전체 API p95 < 100ms, 목표 500ms 대비 충분한 여유

### Indexing Optimization (Before → After)

**content-list API**

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| **p95 응답 시간** | 215.19ms | 36.58ms | **-83.0%** |
| Postgres CPU | 85.53% | 15.22% | **-82.2%** |
| App CPU | 134.50% | 56.38% | **-58.1%** |
| 처리량 (req/s) | 127.3 | 140.2 | **+10.1%** |

```sql
-- 적용한 Partial 복합 인덱스
CREATE INDEX idx_contents_active_rating
    ON contents (average_rating DESC, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_contents_active_created
    ON contents (created_at DESC, id)
    WHERE deleted_at IS NULL;
```

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## Test Coverage

| Category | Coverage | Details |
|----------|----------|---------|
| **Overall** | **85.2%** | LINE 기준, 841 Tests |
| JaCoCo Gate | 80% | CI에서 미달 시 빌드 실패 |
| Integration Tests | 18 | TestContainers (PostgreSQL, Kafka) |
| Load Tests | 9 APIs | k6 부하 테스트 |

### Testing Strategy

- **Unit Tests** — Service 계층 비즈니스 로직 검증
- **Integration Tests** — TestContainers로 실제 PostgreSQL/Kafka 환경에서 테스트
- **Load Tests** — k6로 9개 API 부하 테스트, VU 70까지 스케일링

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## Code Quality

| Tool | Purpose | Configuration |
|------|---------|---------------|
| **JaCoCo** | 테스트 커버리지 측정 | 80% 미달 시 빌드 실패 |
| **Checkstyle** | 코딩 컨벤션 검사 | Naver 코딩 컨벤션 규칙 |
| **SpotBugs** | 정적 분석 (잠재적 버그) | CI 파이프라인 통합 |
| **CodeRabbit** | AI 기반 코드 리뷰 | PR 자동 리뷰 |
| **Codecov** | 커버리지 리포트 시각화 | GitHub PR 코멘트 |

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## CI/CD Pipeline

### CI (Continuous Integration)

```
PR / Push to dev
    └─▶ GitHub Actions (ci.yml)
         ├─ Gradle Build
         ├─ Checkstyle (Naver 코딩 컨벤션)
         ├─ SpotBugs (정적 분석)
         ├─ JaCoCo Coverage Check (≥ 80%)
         ├─ Codecov Report Upload
         └─ ✅ Merge 가능
```

### CD (Continuous Deployment)

```
Push to release/*
    └─▶ GitHub Actions (deploy.yml)
         ├─ Docker Build (Multi-stage)
         ├─ Push to AWS ECR
         ├─ Render ECS Task Definition
         └─ ECS Rolling Update Deploy
              └─ ✅ 무중단 배포 완료

Nginx 설정 변경 시
    └─▶ GitHub Actions (deploy-nginx.yml)
         ├─ Nginx Docker Build
         ├─ Push to AWS ECR
         └─ ECS Rolling Update
```

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## Monitoring

### Custom Metrics (40+)

Micrometer + Prometheus + Grafana 기반 실시간 모니터링  
> 모든 커스텀 메트릭은 `mopl.` 접두사를 사용합니다.

| Domain | Metrics | Examples |
|--------|---------|----------|
| **Content** | 조회수, 검색량, 캐시 히트율 | `content.view.count`, `content.cache.hit` |
| **Review** | 리뷰 작성/수정/삭제, 평점 분포 | `review.create.count`, `review.rating.histogram` |
| **Watching** | 세션 수, 시청자 수, 채팅 수 | `watching.session.active`, `watching.chat.count` |
| **Chat** | 메시지 전송량, WebSocket 연결 수 | `chat.message.count`, `websocket.connection.active` |
| **Notification** | 저장, 중복 제외, 실시간 발행 결과 | `notification.saved`, `notification.duplicate.skipped`, `notification.realtime.publish` |
| **SSE** | 연결 수, 생명주기, 실제 전송 결과 | `sse.connections`, `sse.lifecycle`, `sse.send` |
| **Batch** | 실행 결과, 처리 건수, 마지막 성공 시각 | `batch.run`, `batch.items`, `batch.last.success.timestamp` |
| **Redis Pub/Sub** | 발행·수신, 역직렬화 실패, 처리 시간 | `redis.pubsub.publish`, `redis.pubsub.receive`, `redis.pubsub.process` |

### Grafana Dashboard

> 📊 **Grafana 대시보드 스크린샷 추가 예정**

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

<br/>

## Team

<div align="center">

### 🏠 Team 04 — Codeit Sprint Bootcamp 10기

</div>

| | Name | Role | Domains | Key Contributions |
|---|------|------|---------|-------------------|
| <img src="https://github.com/jsj7878.png" width="60" style="border-radius:50%"/> | [**전승주**](https://github.com/jsj7878) | Team Lead | Content, Review, Watching | AWS 배포/인프라, WebSocket 세션 설계, k6 부하 테스트, 인덱싱 최적화 |
| <img src="https://placecats.com/millie/60/60" width="60" style="border-radius:50%"/> | [**이다솔**](https://github.com/LeeDyol) | Developer | Conversation, DM, Follow | Redis 동기화(DLQ), 커스텀 메트릭(DM/Chat/WS) |
| <img src="https://placecats.com/neo/60/60" width="60" style="border-radius:50%"/> | [**박정현**](https://github.com/JungH200000) | Developer | Playlist, Notification, SSE | Kafka 알림 멱등 처리, Redis Pub/Sub 기반 SSE 멀티서버 대응, 커스텀 메트릭·Grafana 구축 |
| <img src="https://placecats.com/bella/60/60" width="60" style="border-radius:50%"/> | [**박나경**](https://github.com/parkngg) | Developer | Auth, User | JWT/OAuth2(Google, Kakao), Redis 세션 마이그레이션, k6 유저 플로우 시나리오 |

> **프로젝트 기간**: 2026.06.18 ~ 2026.07.29 (6주)

<p align="right">(<a href="#-table-of-contents">back to top</a>)</p>

---

<div align="center">


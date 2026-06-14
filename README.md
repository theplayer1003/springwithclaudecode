# springwithclaudecode

게시판 REST API 하나를 점진적으로 확장하며 Spring 백엔드의 핵심을 구현한
**Spring Boot 학습 프로젝트**입니다. 기본 CRUD에서 시작해 인증·인가, 캐싱,
비동기·메시지 큐, 배치 처리까지 단계(Phase)별로 기능을 쌓아 올립니다.

각 Phase의 학습 내용은 직접 작성한 [Phase 정리 문서](#phase-정리-문서)로 남겼습니다.

## 기능

| 영역 | 내용 |
| --- | --- |
| 기본 | 게시글·댓글 CRUD, 계층 분리(Controller→Service→Repository), DTO/Entity 분리 |
| 데이터 | JPA, 연관관계 매핑(게시글↔댓글), 영속성 컨텍스트·트랜잭션 |
| 예외/검증 | `@RestControllerAdvice` 글로벌 예외 처리, Bean Validation, 커스텀 예외 |
| 인증/인가 | Spring Security, JWT 토큰 인증, Role 기반 인가(작성자/관리자) |
| 테스트 | JUnit 5, Mockito 단위 테스트, `@SpringBootTest` 통합, `@WebMvcTest` |
| 성능 | N+1 해결, 페이징, 인덱스, Redis 캐싱 |
| 비동기/메시징 | `@Async`, Spring Event, RabbitMQ 기반 이메일/SMS 알림 컨슈머 분리 |
| 배치 | Spring Batch(오래된 게시글 아카이빙), No-Offset 페이징 Reader |

## 기술 스택

- Java, Spring Boot 3.5
- Spring Data JPA, Spring Security, Spring Batch
- H2 (학습용 인메모리), Redis, RabbitMQ
- Gradle (Kotlin DSL), JUnit 5 / Mockito

## 실행

외부 의존성으로 Redis, RabbitMQ가 필요합니다.
H2는 인메모리로 동작합니다.

```bash
./gradlew bootRun     # 애플리케이션 실행
./gradlew test        # 테스트 실행
```

## API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/auth/signup` | 회원가입 |
| POST | `/auth/login` | 로그인 (JWT 발급) |
| POST | `/posts` | 게시글 작성 |
| GET | `/posts` | 게시글 목록 (페이징) |
| GET | `/posts/{id}` | 게시글 단건 조회 |
| PUT | `/posts/{id}` | 게시글 수정 (작성자) |
| DELETE | `/posts/{id}` | 게시글 삭제 (작성자) |
| GET | `/posts/search` | 게시글 검색 |
| GET | `/posts/my` | 내가 쓴 게시글 조회 |
| POST | `/posts/{postId}/comments` | 댓글 작성 |
| GET | `/posts/{postId}/comments` | 댓글 목록 |
| GET | `/posts/{postId}/comments/{commentId}` | 댓글 단건 조회 |
| PUT | `/posts/{postId}/comments/{commentId}` | 댓글 수정 (작성자) |
| DELETE | `/posts/{postId}/comments/{commentId}` | 댓글 삭제 (작성자) |
| DELETE | `/admin/posts/{id}` | 게시글 삭제 (관리자) |
| DELETE | `/admin/posts/comments/{commentId}` | 댓글 삭제 (관리자) |

## 프로젝트 구조

```
src/main/java/com/study/board
├── controller   # REST 엔드포인트 (게시글/댓글/인증/관리자)
├── service      # 비즈니스 로직
├── repository   # Spring Data JPA
├── entity       # Post, Comment, Member, ArchivedPost ...
├── dto          # 요청/응답 분리
├── security     # Spring Security, JWT 필터/프로바이더
├── event        # Spring 이벤트
├── listener     # 이벤트 리스너
├── consumer     # RabbitMQ 컨슈머 (이메일/SMS 알림)
├── batch        # Spring Batch (아카이빙 Job, No-Offset Reader)
├── config       # Async, Cache, RabbitMQ 설정
└── exception    # 글로벌 예외 처리
```

## 학습 기록

이 저장소는 코드뿐 아니라 학습 과정 전체를 문서로 남겼습니다. 문서는 두 종류입니다.

### Phase 정리 문서

각 Phase를 마칠 때, 출제된 질문에 직접 답을 작성한 문서입니다.
답을 쓰면서 필요한 부분은 추가로 조사해 보강했습니다.

| 문서 | 주제 |
| --- | --- |
| [Phase 1 정리](docs/phase1/Phase01_정리.md) | 스프링 부트 입문 (IoC/DI, REST API) |
| [Phase 2 정리](docs/phase2/Phase02_정리.md) | JPA, 연관관계, 영속성 컨텍스트·트랜잭션 |
| [Phase 3 정리](docs/phase3/Phase03_정리.md) | 계층 구조, DTO 분리, 예외 처리·검증 |
| [Phase 4 정리](docs/phase4/Phase04_정리.md) | Spring Security, JWT, 인가 |
| [Phase 5 정리](docs/phase5/Phase05_정리.md) | 테스트 (단위/통합, Mockito) |
| [Phase 6 정리](docs/phase6/Phase06_정리.md) | 성능·캐싱 (N+1, 페이징, 인덱스, Redis) |
| [Phase 7 정리](docs/phase7/Phase07_정리.md) | 비동기·메시징 (`@Async`, 이벤트, RabbitMQ) + 메타 통찰 |
| [Phase 8 정리](docs/phase8/Phase08_정리.md) | 배치 처리 (Spring Batch, No-Offset Reader) |

### 개념 문서 / QA

소주제마다 누적한 개념 설명과, 막힌 지점을 주제별로 기록한 Q&A입니다.

- [`docs/QA_INDEX.md`](docs/QA_INDEX.md) — 전체 QA 인덱스 (주제별 분류)
- [`docs/review`](docs/review) — 총정리 리뷰 (시나리오 흐름도, 개념 퀴즈)
- [`CLAUDE.md`](CLAUDE.md) — 학습 진행 방식과 Phase 로드맵

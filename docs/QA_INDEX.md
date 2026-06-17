# QA 문서 인덱스

학습 과정에서 생성된 Q&A 문서를 주제별로 분류한 인덱스.

---

## JPA / 영속성 컨텍스트

- [Dirty Checking](phase3/QA_Dirty_Checking.md) — 영속 상태 엔티티의 변경 자동 감지와 UPDATE
- [영속성 컨텍스트 불일치](phase3/QA_영속성_컨텍스트_불일치.md) — deleteById 후 영속성 컨텍스트와 DB 상태 불일치 문제
- [JPA 콜백](phase3/QA_JPA_콜백.md) — @PrePersist, @PreUpdate 동작 원리와 주의사항
- [@Transactional(readOnly)](phase3/QA_Transactional_readOnly.md) — 읽기 메서드에 readOnly를 붙이는 이유
- [delete 메서드 차이](phase3/QA_delete_메서드_차이.md) — deleteById vs delete(entity) 쿼리 차이
- [1차 캐시 동작 기준](review/QA_1차_캐시_동작_기준.md) — Map<ID, Entity> 구조, ID 기반 조회에서만 캐시 히트
- [save() 메서드와 JPA 행동 시나리오](review/QA_save_메서드와_JPA_행동_시나리오.md) — persist/merge/Dirty Checking 판단 기준 5가지 시나리오
- [Spring Data JPA 메커니즘](phase8/QA_Spring_Data_JPA_메커니즘.md) — 메서드 이름 쿼리 파싱/위치 매칭, JPQL ≠ SQL, Pageable 의 DB 측 잘라 반환, OFFSET 깊은 페이지 함정, Page size vs Chunk size, OOM 의 경험적 기준
- [save() 의 isNew 판정과 Persistable](phase8/QA_save_isNew_Persistable.md) — id 가 미리 채워진 엔티티의 merge 함정, Persistable 우회, 추상화의 일반화 가정과 비표준 시나리오
- [deleteById vs deleteAllByIdInBatch](phase8/QA_deleteById_vs_InBatch.md) — deleteById 의 SELECT→DELETE 메커니즘과 4가지 설계 이유, 영속성 컨텍스트 우회의 트레이드오프, 추상화 vs 성능

## Entity / 연관관계

- [Entity 어노테이션](phase4/QA_Entity_어노테이션.md) — @GeneratedValue, @Column, @Enumerated 설정
- [연관관계 방향과 설정](phase4/QA_연관관계_방향과_설정.md) — 양방향/단방향 판단, cascade, orphanRemoval, fetch 전략

## 계층 구조 / DTO

- [DTO와 Entity 분리](phase3/QA_DTO와_Entity_분리.md) — Request/Response DTO 사용 이유, Entity 검증과의 차이
- [CRUD 검증](phase3/QA_CRUD_검증.md) — 메서드별 필요한 검증 항목 (postId 소속 확인 등)

## 예외 처리

- [예외처리 흐름](phase3/QA_예외처리_흐름.md) — Service에서 예외를 던지고 Handler가 처리하는 관심사 분리

## HTTP / 서블릿

- [HTTP 무상태](phase4/QA_HTTP_무상태.md) — Stateless의 이점 (서버 확장, 장애 대응, 리소스 절약)
- [HTTP 상태코드](phase4/QA_HTTP_상태코드.md) — 상태 코드 분류, 자주 쓰는 조합, 401/403/400/409 구분
- [Servlet](phase4/QA_Servlet.md) — HttpServletRequest/Response, Filter와 Controller의 추상화 차이
- [서블릿 요청응답 동작](phase4/QA_서블릿_요청응답_동작.md) — response 직접 작성, return vs doFilter, Filter 예외 처리
- [HttpServletRequest와 RequestBody](review/QA_HttpServletRequest와_RequestBody.md) — 톰캣 래핑(파싱) vs Jackson 역직렬화 구분

## 인증 / 인가 / Spring Security

- [인증 처리 흐름](phase4/QA_인증_처리_흐름.md) — Filter Chain → SecurityContext → Controller 전체 흐름
- [세션과 토큰 보완](phase4/QA_세션과_토큰_보완.md) — Redis 세션 저장소, Refresh Token, 하이브리드 방식
- [SecurityConfig 설정 구조](phase4/QA_SecurityConfig_설정_구조.md) — HttpSecurity 체인, 규칙 순서, H2 설정, 에러 로그 읽기,
  CommandLineRunner, @Value
- [응답코드 디버깅](phase4/QA_응답코드_디버깅.md) — 403 원인 추적, 401/403 구분 설정, 디버깅 순서

## 비밀번호 / 암호화

- [비밀번호 암호화](phase4/QA_비밀번호_암호화.md) — 해싱 vs 암호화, 솔트, BCrypt matches() 동작
- [솔트와 레인보우 테이블](review/QA_솔트와_레인보우_테이블.md) — 솔트가 노출되어도 효과적인 이유, 사전 계산 재사용 차단

## 프로젝트 구성

- [패키지 구조 결정](phase4/QA_패키지_구조_결정.md) — 클래스 배치 판단 기준, config vs init 구분
- [라이브러리 의존성 관리](phase4/QA_라이브러리_의존성_관리.md) — 의존성 검색 방법, implementation vs runtimeOnly

## 테스트

- [ReflectionTestUtils와 Reflection](phase5/QA_ReflectionTestUtils.md) — 단위 테스트에서 private 필드 설정, Reflection 동작 원리
- [@MockBean deprecated와 @MockitoBean](phase5/QA_MockBean_deprecated.md) — deprecated 개념, @MockBean → @MockitoBean 전환 이유

## Spring 동작 메커니즘

- [AOP와 프록시 메커니즘](phase7/QA_AOP_프록시_메커니즘.md) — @Transactional/@Cacheable/@PreAuthorize/@Async 공통 메커니즘, 자기 호출 문제, private/final 제약
- [프록시 — JDK Dynamic Proxy vs CGLIB](phase7/QA_프록시_JDK_vs_CGLIB.md) — 두 프록시 구현 방식의 동작 원리, Spring 자동 선택 기준, 각 방식의 제약
- [@EventListener 어댑터 메커니즘](phase7/QA_EventListener_어댑터_메커니즘.md) — 프록시 vs 어댑터 패턴 차이, 리플렉션 기반 메서드 변환, 이벤트 타입은 메서드 파라미터에서 자동 추출

## 비동기 / 동시성

- [스레드 풀 vs 커넥션 풀](phase7/QA_스레드풀_vs_커넥션풀.md) — 풀링 패턴, 재사용 자원 차이, 풀 가득 찼을 때 동작(대기 vs 거부 정책)
- [CountDownLatch](phase7/QA_CountDownLatch.md) — 자바 표준 동기화 도구, 비동기 테스트에서의 활용 패턴

## 메시지 큐 / 메시징

- [RabbitMQ vs Kafka](phase7/QA_RabbitMQ_vs_Kafka.md) — 두 도구의 본질 차이, 개념 매칭, 같은 시나리오의 코드 비교, 각자 강한 시나리오
- [Kafka 멘탈모델과 RabbitMQ 비교](phase7/QA_Kafka_멘탈모델_RabbitMQ_비교.md) — Consumer Group/파티션의 본질, RabbitMQ 사고로 Kafka를 이해할 때의 함정, 부하 분산/메시지 영속성/구독 모델의 정반대 방향
- [Kafka Consumer Group](phase7/QA_Kafka_Consumer_Group.md) — 컨슈머 협력 단위의 의미, 같은 그룹 안의 부하 분산과 다른 그룹 사이의 독립성, 파티션이 존재하는 세 가지 이유
- [분리의 축들](phase7/QA_분리의_축들.md) — 시간/결합도/프로세스 축의 직교성, 각 축이 푸는 결합의 종류, 호출자 관점의 분리 완성 시점
- [RabbitListener와 Async 안티패턴](phase7/QA_RabbitListener_Async_안티패턴.md) — 메시지 큐의 ack 약속, @RabbitListener 안에서 @Async 호출 시 ack 타이밍 어긋남, 영속 큐에서 휘발 큐로 메시지를 옮기는 위험
- [HTTP 직접 호출 vs 메시지 큐](phase7/QA_HTTP_직접호출_vs_메시지큐.md) — HTTP 의 본질적 특성, 장애 전파의 Little's Law 메커니즘, 메시지 큐의 신뢰성과 이중 쓰기 문제
- [Push vs Pull 모델](phase7/QA_Push_vs_Pull_모델.md) — 메시지 전달 주도권의 차이, RabbitMQ 의 Push 와 Kafka 의 Pull, 각 시스템의 본질과 시나리오 적합성
- [분산 시스템의 이름 계약](phase7/QA_분산시스템의_이름_계약.md) — 멱등 선언의 정확한 의미와 한계, 분산 시스템의 계약 개념, 이름 어긋남의 조용한 실패
- [Exchange Topic 패턴](phase7/QA_Exchange_Topic_패턴.md) — Fanout vs Topic 의 의도 차이, 마이크로서비스 이벤트 라우팅 시나리오, 패턴 매칭 기반 라우팅의 가치

## 테스트 (계속)

- [Mockito Spy와 @Async 충돌](phase7/QA_Mockito_Spy와_Async_충돌.md) — @SpyBean + @Async가 동작하지 않는 함정, CGLIB와 mockito 인터셉터 충돌, interface 분리 해결책

## 데이터 수명 주기 / 아카이빙

- [아카이빙 스냅샷](phase8/QA_아카이빙_스냅샷.md) — 아카이빙의 5가지 활용 시나리오, 규모에 따른 부가/본질의 전환, 스냅샷의 본질(살아있는 도메인과의 단절), 관계 매핑이 스냅샷을 오염시키는 이유, 재사용 가능한 판단 프레임워크

## 자바 기본기

- [제네릭과 와일드카드 PECS](phase8/QA_제네릭_와일드카드_PECS.md) — 제네릭의 컴파일 타임 본질과 타입 소거, 불공변성, 와일드카드 3종, Producer Extends/Consumer Super, ItemWriter 의 Chunk<? extends T> 분석

## 배치 처리 / Spring Batch

- [OFFSET 페이지 시프트와 Keyset Pagination](phase8/QA_OFFSET_페이지_시프트_Keyset.md) — 읽으면서 변경하는 시나리오의 본질적 함정, 위치 기반 vs 값 기반의 차이, 커스텀 ItemReader 구현, lastSeenId 갱신 시점의 두 패턴
- [Cron 표현식 함정](phase8/QA_Cron_표현식_함정.md) — Spring 의 6필드 구조, 초 필드 누락 시 폭주, 흔한 실수 표, @Scheduled 의 cron / fixedRate / fixedDelay 차이
- [Spring Batch Step 메커니즘](phase8/QA_Spring_Batch_Step_메커니즘.md) — Chunk-oriented Step 의 내부 동작, Reader 의 버퍼링 패턴, ChunkOrientedTasklet 으로의 통일, ItemStream+ExecutionContext 의 재시작 메커니즘

## OS / 시스템 / 운영

- [파일 디스크립터 누수와 OS 자원 한도](phase8/QA_파일_디스크립터_누수.md) — FD 의 본질(파일·소켓·DB커넥션의 번호표), soft/hard/커널 3층 한도, 장수 프로세스의 누수 누적과 Too many open files, 프로세스 트리와 자원 회수, lsof 확인 명령어, 누수 디버깅 4단계, Actuator 모니터링 연결 (26일 켜둔 Claude Code 세션이 계기)

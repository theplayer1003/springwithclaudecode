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

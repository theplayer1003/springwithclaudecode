# Spring Data JPA 메커니즘 — 메서드 이름 쿼리, Pageable, OFFSET 함정, Page vs Chunk

## 질문 배경

Phase 8 의 배치 시나리오에서 `PostRepository` 에 다음 메서드를 추가하며 생긴 의문들을 정리한 문서.

```java
Page<Post> findByCreatedAtBefore(LocalDateTime cutoff, Pageable pageable);
```

한 줄짜리 메서드 선언인데, 그 안에 *선언적 프로그래밍*, *JPQL 의 추상화*, *DB 가 데이터를 잘라 반환하는 메커니즘*, *대량 처리의 page/chunk 분리* 같은 깊은 개념이 담겨 있다. 자기가 작성한 *한 줄의 코드를 자기 언어로 풀어내며* 떠오른 의문들을 정리.

---

## 1. 메서드 이름 쿼리 (Query Method) 의 작동 원리

### 핵심 메커니즘

Spring Data JPA 는 메서드 이름을 **토큰 단위로 파싱** 한다.

```
findByCreatedAtBefore
└── find        ← 동작 (= SELECT)
└── By          ← WHERE 절 시작 신호
└── CreatedAt   ← 엔티티의 필드명 (camelCase 로 인식)
└── Before      ← 비교 키워드 (< 매핑)
```

부팅 시점에 이 파싱 결과로 **JPQL 을 생성** 한다:

```jpql
SELECT p FROM Post p WHERE p.createdAt < :cutoff
```

→ 런타임에 JPA 가 이 JPQL 을 *실제 SQL 로 변환* → DB 로 전송.

### 키워드 매핑 표 (자주 쓰는 것들)

| 메서드 이름 키워드 | JPQL 연산자 | 비고 |
|--|--|--|
| `LessThan` | `<` | |
| `LessThanEqual` | `<=` | |
| `GreaterThan` | `>` | |
| `Before` | `<` | LessThan 과 동일 (시간 타입 가독성) |
| `After` | `>` | GreaterThan 과 동일 |
| `Between` | `BETWEEN ... AND ...` | 파라미터 2개 요구 |
| `Like`, `Containing` | `LIKE '%...%'` | |
| `In` | `IN (...)` | Collection 파라미터 |
| `IsNull`, `IsNotNull` | `IS NULL` | 파라미터 없음 |

→ `Before` 는 *strict less than (<)*. *equal 불포함*.

### JPQL ≠ SQL — 추상화의 한 겹

JPA 가 만드는 건 SQL 이 아닌 **JPQL (Java Persistence Query Language)**. *엔티티 객체* 를 다루는 SQL-비슷한 언어.

```jpql
SELECT p FROM Post p WHERE p.createdAt < :cutoff
```

- 테이블명이 아닌 *엔티티 클래스명* (`Post`)
- 컬럼명이 아닌 *엔티티 필드명* (`p.createdAt`)
- JPA 가 런타임에 *실제 SQL 로 변환* → DB 로 전송

→ JPQL 은 *DB 종속성을 끊는 추상화*. MySQL 이든 PostgreSQL 이든 같은 JPQL 이 동작.

---

## 2. 파라미터 매칭 — *위치가 전부*

### 의문: *같은 타입 파라미터가 여러 개면 어떻게 구분?*

JPA 는 **타입으로 매칭하지 않는다**. 메서드 이름의 키워드 등장 순서 = 파라미터 등장 순서로 1:1 매칭.

#### 예시 1 — Between (한 키워드, 2개 파라미터)

```java
Page<Post> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
```

- 첫 번째 파라미터 `start` → `Between` 의 시작값
- 두 번째 파라미터 `end` → `Between` 의 끝값

#### 예시 2 — 여러 필드, 같은 타입

```java
Page<Post> findByCreatedAtAfterAndUpdatedAtBefore(
    LocalDateTime t1,  // ← CreatedAtAfter 에 매칭
    LocalDateTime t2,  // ← UpdatedAtBefore 에 매칭
    Pageable pageable
);
```

타입이 둘 다 `LocalDateTime` 이어도 **순서로 구분**. *컴파일은 통과하지만 순서를 잘못 쓰면 의미가 뒤바뀌는 버그*.

### 위치 기반의 함정 → 도구 전환 시점

> *조건이 3~4개 이상으로 늘어나면 메서드 이름이 길어지고, 순서 매칭의 위험도 커진다.*
> *그때는 `@Query` 로 전환.*

`@Query` 의 JPQL 에서는 `@Param("name")` 으로 *이름 기반 매칭* — 더 안전하고 가독성도 좋다.

**판단 기준**: *"메서드 이름이 너무 길어지기 시작했다 → 다른 도구로 갈 시점"*.

---

## 3. Pageable — *DB 가 잘라 반환한다*

### 의문: *100만건이 와서 무언가가 20건만 거르는 건가?*

**아니다**. *애플리케이션이 받는 데이터의 양은 DB 가 결정* 한다.

```sql
-- Pageable(pageNumber=2, pageSize=20) → 다음 SQL 생성
SELECT * FROM post ORDER BY created_at DESC LIMIT 20 OFFSET 40;
```

DB 엔진이 `LIMIT 20 OFFSET 40` 을 보고 *41~60번째 행 20개만 반환*. **100만건이 애플리케이션에 도달한 적이 없다**.

이건 흔한 오해 — *"전부 받아서 잘라낸다"* 가 아니라 *"DB 가 보낼 양을 통제한다"*.

### Pageable ≠ LazyLoading

자주 헷갈리는 두 개념. **완전히 다른 메커니즘**:

| | **Pageable** | **LazyLoading** |
|--|--|--|
| 대상 | 같은 엔티티의 *여러 행* | *연관 관계 객체* |
| 잘라내는 단위 | 행(row) 개수 | 객체 그래프의 깊이 |
| 트리거 | 쿼리 실행 시점에 한 번 | 연관 객체 접근할 때마다 |
| 목적 | 대량 데이터의 *분할 조회* | 불필요한 연관 객체 *지연 로딩* |
| SQL | `LIMIT + OFFSET` 추가 | 접근 시점에 *추가 쿼리* |

```java
// Pageable: 한 번에 가져올 행 수 제한
Page<Post> posts = postRepository.findAll(Pageable.of(0, 20));  // 20개만

// LazyLoading: 연관 객체 접근 시점에 추가 쿼리
Post post = postRepository.findById(1L);
post.getMember().getUsername();  // ← 이 순간에 Member 쿼리 발생
```

→ *Pageable 의 "다음 페이지"* 와 *LazyLoading 의 "지연 로딩"* 은 닮아 보이지만 *해결하는 문제가 다르다*.

---

## 4. OFFSET 의 함정 — 깊은 페이지의 비효율

### 메커니즘

```sql
LIMIT 20 OFFSET 1000000
```

→ DB 가 *100만 개를 읽고 그 자리를 건너뛴 다음, 20개를 반환*. **반환은 20개지만 내부 작업은 100만 건**.

→ **깊은 페이지일수록 느려진다**.

### 해결책 — Keyset Pagination (커서 페이징)

```sql
-- OFFSET 대신 마지막 본 id 를 기준으로 다음 페이지
SELECT * FROM post WHERE id < :lastSeenId ORDER BY id DESC LIMIT 20;
```

- 인덱스 활용 가능 (id 가 인덱스되어 있으면 O(log n))
- 페이지 깊이와 무관하게 일정한 성능
- 단점: *임의 페이지로 점프 불가* (이전/다음 만 가능)

### 학습 수준에서

기본 OFFSET 페이징으로 충분. 다만 *대량 배치에서 OFFSET 이 누적되면 비효율* 이라는 점을 기억할 가치는 있다.

> **실무에서 "마지막 페이지가 느려지는 신비한 현상" 의 원인이 대부분 이것.**

---

## 5. Page Size vs Chunk Size (Spring Batch)

### 두 개념의 분리

| | **Page Size** | **Chunk Size** |
|--|--|--|
| 책임 | DB I/O 한 번에 가져올 양 | 트랜잭션 + 처리 단위 |
| 압박 받는 자원 | DB 부하, 네트워크 | 애플리케이션 메모리, 트랜잭션 로그 |
| 구현 위치 | `RepositoryItemReader.pageSize` | `Step.chunk(N)` |

### 같게 맞춰도 되나?

**대체로 맞다**. 실무 디폴트는 *page size = chunk size 로 맞추는 것*. 분리되어 있는 이유는 *튜닝 자유도* 일 뿐이지, *항상 분리해야 하는 게 아니다*.

### 분리의 이유 (튜닝 시점에 등장)

만약 `chunk=100` 인데 *DB 부하가 부담된다면* `page=20` 으로 줄여 *5번 나눠* 가져옴.
*처리는 한 번에 100건씩, DB I/O 는 작게 끊어서*.

이런 *분리된 튜닝* 이 가능한 게 두 개념의 분리 이유.

### 디폴트

> **page size = chunk size**. 가장 단순하고 효율적.
> 추후 튜닝 필요해지면 그때 분리.

---

## 6. OOM 의 경험적 기준

### 솔직한 사실

**측정 없이는 정답이 없다**. 시스템마다 *엔티티 크기, JVM 힙 크기, 동시 처리량* 이 모두 다름.

### 그래도 *경험적 안전선*

#### Post 엔티티 메모리 추정

| 1건 | ~2KB ~ ~10KB (title + content + 메타데이터, 평균) |
|--|--|
| 1만 건 | 20MB ~ 100MB |
| 10만 건 | 200MB ~ 1GB ← *위험권 진입* |
| 100만 건 | 2GB ~ 10GB ← *명백히 OOM* |

#### 디폴트 청크 사이즈

> **chunk size = 100 ~ 1000** 이 대부분의 시스템에서 안전 + 효율의 균형점.

Spring Batch 공식 권장도 *10~200* 사이. 보수적으로 100, 처리량 우선이면 500~1000.

### 실무에서 *진짜* 기준 만드는 법

- *VisualVM*, *JFR (Java Flight Recorder)*, *힙 덤프* 로 실제 엔티티 크기 측정
- *100건 처리 시 200MB 사용된다 → 1000건은 2GB → 위험* 같은 측정 기반 추정
- *튜닝* 으로 점진적으로 늘려가며 OOM 직전까지 최적화

> **기준은 시스템마다 다르고, 측정해야 알 수 있다.**

학습 수준에서는 *100* 정도가 안전 디폴트.

---

## 7. 메타 통찰 — *한 줄의 코드 안에 담긴 추상화*

```java
Page<Post> findByCreatedAtBefore(LocalDateTime cutoff, Pageable pageable);
```

이 *한 줄* 안에 담긴 것들:

1. **선언적 프로그래밍** — 메서드 이름만으로 *원하는 결과* 를 선언, *어떻게(how)* 는 프레임워크가 채움
2. **JPQL ≠ SQL** — 엔티티 추상화 위에서 동작, DB 종속성 끊김
3. **Pageable ≠ LazyLoading** — 표면적 유사성에 속지 말 것
4. **DB 가 양을 통제** — 애플리케이션이 *받는 양* 자체를 DB 가 결정
5. **OFFSET 의 함정** — 깊은 페이지일수록 비효율
6. **page size ≠ chunk size** — Spring Batch 의 이중 제어

> *코드 한 줄이 짧을수록 그 안에 담긴 추상화는 깊다.*
> 짧은 코드를 *자기 언어로 풀어내며* 의문을 발견하는 게 학습의 본질적 자세.

# deleteById vs deleteAllByIdInBatch — JPA 추상화와 성능의 트레이드오프

## 질문 배경

Phase 8 의 아카이빙 Writer 구현 시:

```java
// 학습자의 첫 시도 (단건 호출)
for (ArchivedPost archivedPost : chunk) {
    Long id = archivedPost.getId();
    postRepository.deleteById(id);
    archivedPostRepository.save(archivedPost);
}
```

이게 *대량 처리에 적합한가* 의 의문에서 출발.

또한 `deleteById` 가 *이름과 다르게 SELECT 부터 수행* 한다는 점이 발견됨. **왜 그렇게 설계됐는가** 가 핵심 질문.

---

## 1. deleteById 의 실제 동작

### Spring Data JPA 의 구현

```java
// SimpleJpaRepository.deleteById
public void deleteById(ID id) {
    Assert.notNull(id, ...);
    findById(id).ifPresent(this::delete);  // ← SELECT 후 delete(entity)
}

// delete(T entity)
public void delete(T entity) {
    if (entityManager.contains(entity)) {
        entityManager.remove(entity);
    } else {
        T managed = entityManager.merge(entity);  // 영속 상태로 만들고
        entityManager.remove(managed);            // 제거
    }
}
```

→ **반드시 엔티티가 영속성 컨텍스트에서 managed 상태여야** JPA 가 DELETE 쿼리 생성.

### 결과

`deleteById(5L)` 호출 시 일어나는 일:
1. `findById(5L)` → **SELECT** 쿼리 DB 에 전송
2. 결과를 영속성 컨텍스트에 올림
3. `delete(entity)` 호출 → 영속성 컨텍스트의 엔티티에 *삭제 표시*
4. 트랜잭션 커밋 시점에 **DELETE** 쿼리 DB 에 전송

→ *한 건 삭제에 SELECT + DELETE 2번의 쿼리*.

---

## 2. *왜 그렇게 설계됐는가* — 4가지 이유

이름만 보면 *DELETE 한 번* 이면 끝날 일을 *왜 SELECT 부터 거치는가*. 학습자의 핵심 질문.

### 이유 1 — 라이프사이클 콜백 보장

JPA 는 *엔티티의 생명주기 이벤트* 에 콜백을 걸 수 있게 해줌:

```java
@Entity
public class Post {
    @PreRemove
    public void onRemove() {
        // 삭제 직전 처리 (예: 외부 리소스 정리, 로그 기록)
    }
    
    @PostRemove
    public void afterRemove() {
        // 삭제 직후 처리
    }
}
```

`@PreRemove`, `@PostRemove` 같은 콜백은 **영속 상태의 엔티티에서만 동작**. 콜백을 호출하려면 *반드시 객체로 가져와야* 함.

### 이유 2 — Cascade 연관관계 처리

`cascade = REMOVE` 가 걸린 연관관계가 있으면 *연관 엔티티들도 함께 삭제* 해야 함:

```java
@Entity
public class Post {
    @OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE)
    private List<Comment> comments;
}
```

`Post` 를 삭제하면 `Comments` 도 함께 삭제. *연관 객체 그래프를 탐색* 하려면 *엔티티 객체가 영속성 컨텍스트에 있어야* 함.

### 이유 3 — 객체지향 추상화의 일관성

JPA 의 원칙:

> **"DB 의 row 가 아니라 *객체* 를 다룬다."**

객체를 다루려면 *객체를 가져와서, 객체를 제거* 가 일관된 방식. *DELETE SQL 을 직접 날리는 것* 은 *추상화를 깨는 행위*.

### 이유 4 — 영속성 컨텍스트와의 정합성

영속성 컨텍스트는 *변경 사항을 추적* 함 (Dirty Checking). 만약 *컨텍스트 모르게 DELETE 가 일어나면*, 컨텍스트에 *유령 객체* 가 남아 불일치 발생.

```
시나리오 (만약 deleteById 가 SELECT 없이 바로 DELETE 한다면):
1. findById(5L) 로 Post(5) 를 영속성 컨텍스트에 올림
2. deleteById(5L) → DB 에서만 DELETE
3. 영속성 컨텍스트에는 여전히 Post(5) 가 살아있음 (유령)
4. 같은 트랜잭션에서 post5.setTitle(...) → Dirty Checking → UPDATE 시도
5. 이미 사라진 row 를 UPDATE → 묘한 에러
```

이런 *유령 상태* 를 막기 위해 *SELECT 부터 거치게* 설계.

### 한 줄 요약

> **`deleteById` 는 *DB 의 row 를 지우는 것* 이 아니라 *영속성 컨텍스트의 객체를 통해 JPA 가 DELETE 를 만들어내도록 하는 것*. JPA 의 객체지향 추상화의 일부.**

---

## 3. 대량 처리에서의 문제 — N + N 쿼리

학습자의 단건 호출 코드:

```java
for (ArchivedPost archivedPost : chunk) {  // chunk = 1000건
    postRepository.deleteById(id);        // SELECT + DELETE
    archivedPostRepository.save(archivedPost);  // SELECT(merge) + INSERT
}
```

1000건 처리 시:
- `deleteById` × 1000 → 2000 쿼리 (SELECT 1000 + DELETE 1000)
- `save` × 1000 → 1000~2000 쿼리

**총 3000~4000번의 DB 왕복** — 대량 처리에서 *치명적 비효율*.

---

## 4. 해결책 — `deleteAllByIdInBatch`

Spring Data JPA 가 제공하는 **벌크 삭제 메서드**:

```java
postRepository.deleteAllByIdInBatch(List.of(1L, 2L, ..., 1000L));
```

생성되는 SQL:

```sql
DELETE FROM post WHERE id IN (1, 2, 3, ..., 1000);
```

→ **1000건 삭제가 단일 쿼리 1번**.

### 동작 메커니즘

`deleteAllByIdInBatch` 는 **영속성 컨텍스트를 우회**:
- SELECT 없음
- 영속성 컨텍스트에 올리지 않음
- *직접 JPQL DELETE 를 날림*

생성되는 JPQL:
```jpql
DELETE FROM Post p WHERE p.id IN :ids
```

→ JPA 가 *바로 DELETE 쿼리* 로 변환해 실행.

---

## 5. `deleteAllById` vs `deleteAllByIdInBatch`

이름이 비슷한 두 메서드. 헷갈리기 쉬워서 정리:

| | `deleteAllById` | `deleteAllByIdInBatch` |
|--|--|--|
| 영속성 컨텍스트 | 활용 (각 id 마다 SELECT → 컨텍스트에 올림 → DELETE) | **우회** (바로 DELETE) |
| 쿼리 수 | **N번** (각 id 마다 SELECT + DELETE) | **1번** (DELETE IN) |
| 라이프사이클 콜백 | `@PreRemove` 등 작동 | 콜백 **무시** |
| Cascade | 작동 | **무시** |
| 사용 시점 | 일반 시나리오 | **대량 배치** |

---

## 6. 추상화 vs 성능의 트레이드오프

### 두 메서드의 본질적 차이

- **`deleteById`**: *추상화 우선*. 콜백, Cascade, 영속성 컨텍스트 일관성 모두 보장. *느림*.
- **`deleteAllByIdInBatch`**: *성능 우선*. 추상화 우회. *빠름*.

### 언제 무엇을 쓰는가

```
일반 비즈니스 로직 (한두 건 삭제, 콜백/Cascade 필요)
    → deleteById, deleteAllById

대량 배치 처리 (수천~수억 건, 콜백 불필요)
    → deleteAllByIdInBatch
```

> **추상화의 가치 vs 성능의 가치**. *대부분의 경우* 추상화의 가치가 큼. *대량 처리* 에서만 *성능* 이 우선.

### 학습자 시나리오의 선택

아카이빙 시나리오:
- 1000건 / 청크 → 대량 처리
- 콜백 불필요 (Post 의 `@PreRemove` 없음)
- Cascade 불필요 (Comment 처리 정책에서 별도 처리)

→ **`deleteAllByIdInBatch` 가 정답**.

---

## 7. 메타 통찰 — *추상화 + 우회 사다리* 패턴

### JPA 의 설계 철학

JPA 의 모든 메서드는 *추상화 정도* 의 스펙트럼 위에 자리잡고 있다:

```
[강한 추상화]                              [약한 추상화 (성능 우선)]
deleteById  →  delete(entity)  →  ...  →  deleteAllByIdInBatch  →  @Query DELETE
SELECT+DELETE   영속성 컨텍스트                  IN 절 벌크          직접 JPQL
```

### *우회 사다리* 가 의도적으로 마련됨

JPA 의 설계자는 *대량 처리 시나리오* 에서 *추상화의 비용이 가치를 넘어선다* 는 걸 안다. 그래서 **`InBatch` 메서드를 명시적으로 제공**.

> *우회용 사다리를 의도적으로 만들어놓은 것*. 우회 자체가 부정적인 게 아니라 *정당한 도구 선택*.

### 도구 선택의 판단 능력

> **언제 추상화를 따르고 언제 깰지의 판단** 이 *기술의 사용법을 넘어 *판단 능력* 으로 이어지는 부분*.

Phase 7 의 *"결합도 0 은 환상"*, *"학습 코드 ≠ 실전 의사결정"* 과 같은 결의 메타 통찰. *맥락에 따라 도구를 선택* 하는 능력이 기술 사용의 핵심.

---

## 8. 일반화되는 통찰

이 패턴은 *JPA 만의 이야기가 아님*. 다른 영역에서도 유사한 *추상화 + 우회* 패턴이 발견됨:

- **Hibernate**: `session.delete()` vs `Query.executeUpdate("DELETE ...")` 직접 쿼리
- **Spring Data JPA**: `findAll()` vs `JdbcTemplate.queryForList()` 저수준 우회
- **Redis Spring Data**: `RedisTemplate` 추상화 vs `Lettuce` 직접 사용

> **추상화는 *보통의 경우* 를 단순화. *대량/특수 시나리오* 에서는 *의도적 우회*. 두 가지가 한 프레임워크 안에 공존하는 것이 *성숙한 라이브러리 설계*.**

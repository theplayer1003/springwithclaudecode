# JPA save() 의 isNew 판정과 Persistable — id 가 미리 채워진 엔티티의 함정

## 질문 배경

Phase 8 의 아카이빙 배치 실행 중 SQL 로그에서 *예상치 못한 SELECT 쿼리* 가 발견됨:

```sql
select ap1_0.id, ap1_0.archived_at, ...
from archived_post ap1_0 
where ap1_0.id=?
```

배치의 의도는 *Post → ArchivedPost 변환 후 새 테이블에 INSERT* 인데, **왜 ArchivedPost 테이블에 SELECT 가 나가는가** 라는 의문.

원인은 *`saveAll` 의 내부 동작* 과 *JPA 의 `isNew()` 판정* 에 있다.

---

## 1. save() 의 내부 동작 — *persist vs merge*

Spring Data JPA 의 `SimpleJpaRepository.save()` 구현:

```java
public <S extends T> S save(S entity) {
    Assert.notNull(entity, ...);
    if (entityInformation.isNew(entity)) {
        em.persist(entity);   // 새 엔티티: INSERT
        return entity;
    } else {
        return em.merge(entity);  // 기존 엔티티: SELECT 후 INSERT/UPDATE
    }
}
```

→ **`isNew()` 의 판정 결과에 따라 *persist* 또는 *merge*** 가 분기.

### `isNew()` 의 기본 판정 기준

JPA 가 *새 엔티티인지* 판단하는 기본 규칙:

| 조건 | 판정 |
|--|--|
| `@Id` 필드가 `null` (또는 primitive 0) | **새 엔티티** → `persist` |
| `@Id` 필드에 값이 있음 | **기존 엔티티** → `merge` |

### merge 의 동작

```
1. 영속성 컨텍스트에서 같은 id 의 엔티티 찾기
2. 없으면 DB 에서 SELECT
3. 있으면 그 엔티티에 변경 사항 머지
4. 없는 채로 DB 에 없으면 → INSERT
```

→ *DB SELECT 가 필연적으로 발생*.

---

## 2. 학습자 시나리오의 비표준성

학습자의 `ArchivedPost` 설계:

```java
@Entity
public class ArchivedPost {
    @Id
    private Long id;  // ← 원본 Post 의 id 그대로 보존 (= 미리 채워짐)
    // ...
}
```

Processor 에서:

```java
return new ArchivedPost(post.getId(), ...);  // ← id 가 채워진 채로 생성
```

→ `id != null` 인 상태로 Writer 의 `saveAll(items)` 에 전달.

### 결과

`saveAll` 내부 `save` 호출 시:

1. `isNew(item)` 판정 → **false** (id 가 null 아니므로 *기존 엔티티로 잘못 판단*)
2. `merge` 호출
3. **DB 에서 SELECT** (영속성 컨텍스트 확인 → DB 조회)
4. 없으니까 → INSERT

```
의도:  Post 변환 → ArchivedPost INSERT
실제:  Post 변환 → SELECT (헛수고) → ArchivedPost INSERT
                   ↑ 불필요한 N번
```

1500건 처리 시 **1500번의 불필요한 SELECT 추가**. *성능 낭비*.

---

## 3. 해결책 — Persistable 인터페이스

Spring Data Commons 의 `Persistable<ID>` 인터페이스:

```java
public interface Persistable<ID> {
    @Nullable
    ID getId();
    
    boolean isNew();
}
```

엔티티가 이 인터페이스를 구현하면 **`isNew()` 의 기본 판정 대신** 사용자가 정의한 `isNew()` 결과를 사용.

### ArchivedPost 에 적용

```java
import org.springframework.data.domain.Persistable;

@Entity
public class ArchivedPost implements Persistable<Long> {
    
    @Id
    private Long id;
    
    // ... 기존 필드들
    
    @Override
    public Long getId() {
        return id;
    }
    
    @Override
    public boolean isNew() {
        return true;  // 항상 새 엔티티로 취급
    }
}
```

### 결과

`saveAll` 호출 시:
1. `isNew(item)` 판정 → **true** (Persistable.isNew() 결과)
2. `persist` 호출
3. **DB SELECT 없이 바로 INSERT**

1500번의 SELECT 가 *사라짐*.

---

## 4. 안전성 — *항상 true 가 위험하지 않은가?*

일반적인 시나리오에서는 위험합니다:
- 이미 있는 id 에 `persist` 하면 → *PK 충돌* (DuplicateKeyException)

그러나 학습자의 *아카이빙 시나리오* 에서는 안전:

- 한 Post 는 *생애 한 번만 아카이빙* 됨
- 아카이빙 후 원본 Post 는 *DELETE 됨*
- 즉, 같은 id 의 ArchivedPost 가 *두 번 INSERT 될 일이 없음*

→ **시나리오의 제약이 안전성을 보장**.

### 조건부 isNew

더 안전한 패턴은 *조건부 isNew*:

```java
@Override
public boolean isNew() {
    return archivedAt == null;  // archivedAt 이 채워지지 않은 상태면 새 엔티티
}
```

이 경우 *@PrePersist 가 archivedAt 을 채우는 패턴* 과 결합. 영속화 전후의 상태를 구분.

학습자의 시나리오에서는 *archivedAt 이 Processor 에서 이미 채워짐* 이라 위 패턴은 안 맞고, *항상 true* 가 적합.

---

## 5. 메타 통찰 — *추상화의 일반화 가정*

### JPA 의 "보통의 경우" 가정

JPA 의 `isNew()` 기본 판정은 *@Id 가 @GeneratedValue 로 자동 생성* 되는 시나리오에 맞춰져 있음:

```java
// JPA 가 가정하는 "보통의 경우"
@Entity
public class Post {
    @Id
    @GeneratedValue(strategy = IDENTITY)  // ← DB 가 id 자동 생성
    private Long id;  // save 호출 시점에 null
}
```

- save 시점에 `id == null` → *새 엔티티* → persist → INSERT 후 id 채워짐
- 이후 update 시 `id != null` → *기존 엔티티* → merge → SELECT + UPDATE

이 패턴이 *대부분의 시나리오에 맞기 때문에* JPA 의 기본 동작.

### 비표준 시나리오 — *id 를 직접 관리*

학습자의 아카이빙 시나리오는 *비표준*:
- *원본 Post 의 id 를 직접 가져와 새 엔티티의 id 로 사용*
- save 시점에 *id 가 이미 채워져 있음*
- 그런데 *DB 에는 없음 (새 엔티티임)*

JPA 의 기본 판정이 *이런 경우를 잘못 해석* → *Persistable 로 명시적 우회*.

### 일반화

> **추상화는 *보통의 경우* 에 맞게 설계됨. 비표준 시나리오에서는 *추상화가 제공하는 우회 메커니즘* 을 의식적으로 사용해야 한다.**

Persistable 은 *JPA 가 의도적으로 마련한 우회 사다리*. *비표준 시나리오의 존재* 를 인정하고 *명시적 신호* 를 제공할 수 있게 함.

이건 *deleteAllByIdInBatch* 의 *영속성 컨텍스트 우회* 와 같은 결의 통찰. JPA 가 *일반 추상화 + 우회 사다리* 의 패턴으로 설계되어 있음.

(자세한 내용은 `QA_deleteById_vs_InBatch.md` 참조.)

---

## 6. 관련 메모리

이전 학습에서 `save` 의 분기 시나리오를 정리한 QA: `review/QA_save_메서드와_JPA_행동_시나리오.md`. 본 문서는 *그 연장선* — *id 가 미리 채워진 비표준 시나리오* 의 추가 사례.

> **같은 `save` 메서드라도 *진입 시점의 엔티티 상태* 가 동작을 결정한다.** persist/merge/Dirty Checking 의 분기를 *진입 시점의 상태* 에서 거꾸로 추론할 수 있어야 진단력이 생긴다.

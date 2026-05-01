# save() 메서드로 보는 JPA 행동 시나리오

## 전제: save()는 만능이 아니다

`save()`는 내부적으로 두 가지 분기를 탄다:

```java
// SimpleJpaRepository 내부 (Spring Data JPA가 자동 생성한 구현체)
public <S extends T> S save(S entity) {
    if (isNew(entity)) {       // ID가 null이면 새 엔티티
        em.persist(entity);    // INSERT
    } else {
        em.merge(entity);      // UPDATE
    }
}
```

---

## 시나리오 1: 새 엔티티 저장

```java
Post post = new Post("제목", "내용", member);  // id = null
postRepository.save(post);
```

```
save() 호출
  ↓
ID가 null → 새 엔티티로 판단
  ↓
@PrePersist 콜백 실행 (createdAt 등 초기화)
  ↓
EntityManager.persist() → 영속성 컨텍스트에 등록 (1차 캐시 저장 + 스냅샷 생성)
  ↓
트랜잭션 커밋 시 INSERT SQL 전송
```

이 시점 이후 `post.getId()`를 호출하면 DB가 생성한 ID 값을 얻을 수 있다.

---

## 시나리오 2: 기존 엔티티 수정 — Dirty Checking (save 불필요)

```java
Post post = postRepository.findById(1L);  // 영속 상태로 조회
post.update("새 제목", "새 내용");          // 필드만 변경
// save() 호출하지 않음!
```

```
findById() → DB에서 조회 → 영속성 컨텍스트에 등록 (1차 캐시 + 스냅샷 저장)
  ↓
post.update() → 메모리상의 엔티티 필드만 변경
  ↓
트랜잭션 커밋 시점:
  현재 엔티티 상태 vs 스냅샷 비교 (Dirty Checking)
  ↓
  달라진 부분 발견 → UPDATE SQL 자동 생성 및 전송
```

영속 상태인 엔티티는 필드만 바꾸면 JPA가 알아서 UPDATE를 해준다.

---

## 시나리오 3: 기존 엔티티에 save() 호출 — merge (불필요한 호출)

```java
Post post = postRepository.findById(1L);
post.update("새 제목", "새 내용");
postRepository.save(post);  // 불필요하지만 호출한 경우
```

```
save() 호출
  ↓
ID가 null이 아님 → 기존 엔티티로 판단
  ↓
EntityManager.merge() 호출
  ↓
이미 영속 상태이므로 실질적으로 아무 일도 안 함
  ↓
트랜잭션 커밋 시 Dirty Checking으로 UPDATE (시나리오 2와 동일)
```

호출해도 에러는 나지 않지만 불필요한 호출이다. Dirty Checking이 이미 해주기 때문이다.

---

## 시나리오 4: 삭제

```java
Post post = postRepository.findById(1L);
postRepository.delete(post);
```

```
delete() 호출
  ↓
EntityManager.remove() → 영속성 컨텍스트에서 "삭제 예정"으로 표시
  ↓
트랜잭션 커밋 시 DELETE SQL 전송
```

`deleteById()`를 쓰면 내부적으로 findById() → delete()를 순서대로 실행한다.

---

## 시나리오 5: 트랜잭션 밖에서의 엔티티 (준영속 상태)

```java
// Service A의 트랜잭션 안에서 조회
Post post = postRepository.findById(1L);
// 트랜잭션 종료 → 영속성 컨텍스트 비워짐 → post는 준영속 상태

// Service B의 새 트랜잭션에서
post.update("새 제목", "새 내용");
// Dirty Checking 안 됨! 영속성 컨텍스트에 없으니까

postRepository.save(post);  // 이때는 merge가 필요
```

```
save() 호출
  ↓
ID가 있음 → merge() 호출
  ↓
DB에서 해당 ID로 SELECT (현재 DB 상태 확인)
  ↓
준영속 엔티티의 값을 영속 엔티티에 복사
  ↓
트랜잭션 커밋 시 UPDATE SQL 전송
```

이 경우에만 save()가 의미 있는 UPDATE 역할을 한다.

---

## @PrePersist와 persist()의 관계

이름이 비슷하지만 다른 개념이다.

- **persist()**: "이 엔티티를 영속 상태로 만들어라" (JPA 핵심 동작)
- **@PrePersist**: "persist() 하기 전에(Pre) 이 메서드를 실행해라" (이벤트 콜백)

```
save() 호출 → 새 엔티티 판단 → @PrePersist 실행 → persist() 실행
```

---

## 정리표

| 시나리오              | ID 상태 | 내부 동작                | SQL    | save() 필요? |
|-------------------|-------|----------------------|--------|------------|
| 새 엔티티 저장          | null  | persist()            | INSERT | 필요         |
| 영속 상태 엔티티 수정      | 있음    | Dirty Checking       | UPDATE | 불필요        |
| 영속 상태에서 save() 호출 | 있음    | merge() (실질적 무동작)    | UPDATE | 불필요        |
| 준영속 엔티티 수정        | 있음    | merge() (DB 조회 후 복사) | UPDATE | 필요         |
| 삭제                | 있음    | remove()             | DELETE | delete 사용  |

**핵심:** 영속 상태인 엔티티는 save() 없이 필드만 바꾸면 된다. save()가 꼭 필요한 경우는 **새 엔티티 저장**과 **준영속 엔티티 재저장** 두 가지뿐이다.

# Phase 3 질문과 답변

---

## Q1. Response DTO에도 검증 어노테이션이 필요한가?

**A:** 필요 없다. 검증은 시스템 경계(외부 입력이 들어오는 지점)에서만 하면 된다.
- Request DTO — 클라이언트가 보내는 데이터 → 신뢰할 수 없음 → 검증 필요
- Response DTO — 서버가 만들어서 보내는 데이터 → 이미 검증된 데이터 → 검증 불필요

---

## Q2. 읽기 전용 메서드에도 @Transactional(readOnly = true)이 필요한가?

**A:** 없어도 동작하지만, 붙이는 것이 실무 관례다.
- Dirty Checking을 건너뛰어 성능 이점이 있음
- 코드를 읽는 사람에게 "이 메서드는 변경하지 않는다"는 의도를 전달
- 실수로 Entity를 수정해도 DB에 반영되지 않아 안전
- 규칙: 쓰기(create/update/delete) → `@Transactional`, 읽기(get/search) → `@Transactional(readOnly = true)`

---

## Q3. Dirty Checking의 동작 흐름

**A:**
1. `findById()` → 영속성 컨텍스트에 있으면 그대로 반환, 없으면 DB에서 SELECT 후 영속성 컨텍스트에 저장
2. 영속성 컨텍스트는 Entity를 저장할 때 **스냅샷(원본 복사본)**도 함께 보관
3. `post.setTitle(...)` 등으로 값 변경 → 이 시점에는 DB에 아무 일도 안 일어남
4. 트랜잭션 커밋 시점 → JPA가 스냅샷과 현재 상태를 비교 → 달라진 필드가 있으면 UPDATE 쿼리 자동 실행

**이점:**
- `save()` 호출을 잊어버려도 자동 반영
- 변경이 없으면 UPDATE 쿼리를 실행하지 않음
- Entity를 일반 자바 객체처럼 다룰 수 있어 비즈니스 로직이 깔끔해짐

---

## Q4. 영속성 컨텍스트 불일치 문제

**A:** 같은 트랜잭션 안에서 DB 상태와 영속성 컨텍스트의 상태가 어긋날 수 있다.

예: `post.getComments()`로 댓글 3개를 로딩 → `deleteById()`로 댓글 1개 삭제 → 영속성 컨텍스트의 Post는 여전히 댓글 3개로 알고 있음.

트랜잭션이 끝나면 영속성 컨텍스트가 사라지므로 다음 요청에서는 정상이지만, 같은 트랜잭션 내에서 "삭제 후 목록 조회" 같은 로직이 있으면 버그가 된다. 불필요한 Entity 로딩을 피하면 문제 자체를 원천 차단할 수 있다.

---

## Q5. delete(entity)와 deleteById(id)의 차이

**A:**
- `deleteById(id)` → 내부에서 `findById()` (SELECT) + `delete()` (DELETE). 총 2단계.
- `delete(entity)` → Entity가 이미 영속성 컨텍스트에 있으면 바로 DELETE 실행.

이미 `findById()`로 Entity를 조회한 상태라면 `delete(entity)`가 SELECT 쿼리 1회를 아낀다.

---

## Q6. Service에서 예외를 던지는 이유 (ErrorResponse를 직접 반환하지 않는 이유)

**A:** 관심사의 분리 때문이다.
- Service는 비즈니스 로직만 담당 → "못 찾았다"는 사실을 예외로 던짐
- GlobalExceptionHandler가 예외를 잡아서 ErrorResponse(DTO)로 변환
- ErrorResponse는 예외가 아니라 클라이언트에게 보내는 JSON 응답 형식일 뿐

이점: 에러 응답 형식을 바꿀 때 Handler 한 곳만 수정하면 됨. Service가 HTTP 응답 형식을 알 필요가 없음.

---

## Q7. CRUD 각 메서드에 필요한 검증

**A:** URL이 `/posts/{postId}/comments/{commentId}`일 때:

| 메서드 | 필요한 검증 | 이유 |
|--------|------------|------|
| create | 게시글 존재 확인 | 없는 게시글에 댓글 불가 |
| getAll | 게시글 존재 확인 | 없는 게시글이면 빈 리스트가 아니라 404 |
| get/update/delete | 댓글 존재 + postId 소속 확인 | URL 조작으로 다른 게시글의 댓글을 조작하는 것을 방지 |

---

## Q8. 콜백(Callback)과 @PreUpdate, @PrePersist

**A:** 콜백은 "특정 시점이 되면 불러달라"는 패턴이다. 시점은 프레임워크가 결정하고, 무엇을 할지는 개발자가 등록한다.

JPA 콜백 어노테이션:
- `@PrePersist` — INSERT 직전에 자동 실행
- `@PreUpdate` — UPDATE 직전에 자동 실행
- `@PreRemove` — DELETE 직전에 자동 실행

자동화의 이점: Entity를 수정하는 곳이 여러 군데 생겨도 누락이 불가능하다. 수동 호출이면 한 곳이라도 빼먹으면 버그가 생기고, 찾기도 어렵다.

주의: `@PreUpdate` 메서드는 JPA가 호출하는 것이므로 Service에서 직접 호출하면 안 된다 (2번 실행됨).

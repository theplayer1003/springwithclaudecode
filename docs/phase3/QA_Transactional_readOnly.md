# @Transactional(readOnly = true)

## Q. 읽기 메서드에도 @Transactional이 필요한가?

**배경:** Service에서 조회 메서드를 작성할 때, 데이터를 읽기만 하는데 `@Transactional`을 붙여야 하는지 의문이 생겼다.

**A:** 없어도 동작하지만, `readOnly = true`를 붙이는 것이 실무 관례다. 세 가지 이점이 있다.

1. **성능** — Dirty Checking을 건너뛰어 불필요한 스냅샷 비교 작업 생략. 또한 DB 레벨에서도 읽기 전용 최적화가 적용될 수 있음
2. **의도 전달** — 코드를 읽는 사람에게 "이 메서드는 데이터를 변경하지 않는다"고 명확히 알림
3. **안전성** — 실수로 Entity를 수정해도 DB에 반영되지 않음

```java
// 쓰기 — @Transactional (Dirty Checking 포함, 롤백 가능)
@Transactional
public PostResponse createPost(PostCreateRequest request, String username) {
    // ...
}

// 읽기 — @Transactional(readOnly = true) (Dirty Checking 생략, 성능 최적화)
@Transactional(readOnly = true)
public PostResponse getPost(Long id) {
    // ...
}
```

**규칙:**

- 쓰기(create/update/delete) → `@Transactional`
- 읽기(get/search) → `@Transactional(readOnly = true)`

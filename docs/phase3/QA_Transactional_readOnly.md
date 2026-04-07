# @Transactional(readOnly = true)

## Q. 읽기 메서드에도 @Transactional이 필요한가?

**A:** 없어도 동작하지만, 붙이는 것이 실무 관례다. 세 가지 이점이 있다.

1. **성능** — Dirty Checking을 건너뛰어 불필요한 비교 작업 생략
2. **의도 전달** — 코드를 읽는 사람에게 "이 메서드는 데이터를 변경하지 않는다"고 명확히 알림
3. **안전성** — 실수로 Entity를 수정해도 DB에 반영되지 않음

규칙:
- 쓰기(create/update/delete) → `@Transactional`
- 읽기(get/search) → `@Transactional(readOnly = true)`

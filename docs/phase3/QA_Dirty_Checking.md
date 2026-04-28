# Dirty Checking

## Q1. Dirty Checking의 동작 흐름은?

**A:**

1. `findById()` → 영속성 컨텍스트에 있으면 그대로 반환, 없으면 DB에서 SELECT 후 영속성 컨텍스트에 저장
2. 영속성 컨텍스트는 Entity를 저장할 때 **스냅샷(원본 복사본)**도 함께 보관
3. `post.setTitle(...)` 등으로 값 변경 → 이 시점에는 DB에 아무 일도 안 일어남
4. 트랜잭션 커밋 시점 → JPA가 스냅샷과 현재 상태를 비교 → 달라진 필드가 있으면 UPDATE 쿼리 자동 실행

## Q2. Dirty Checking으로 얻는 이점은?

**A:**

- `save()` 호출을 잊어버려도 자동 반영
- 변경이 없으면 UPDATE 쿼리를 실행하지 않음
- Entity를 일반 자바 객체처럼 다룰 수 있어 비즈니스 로직이 깔끔해짐

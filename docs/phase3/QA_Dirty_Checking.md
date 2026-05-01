# Dirty Checking

## Q1. Dirty Checking이란 무엇이며, 동작 흐름은?

**배경:** 게시글 수정 기능을 구현할 때 `save()`를 호출하지 않았는데 DB에 UPDATE가 반영되었다. JPA가 어떻게 변경을 감지하는지 의문이 생겼다.

**A:** Dirty Checking은 JPA가 영속 상태인 엔티티의 변경을 자동으로 감지하여 UPDATE 쿼리를 생성하는 기능이다.

```
1. findById(1L) 호출
   → DB에서 SELECT → 영속성 컨텍스트에 엔티티 저장 + 스냅샷(원본 복사본) 보관

2. post.update("새 제목", "새 내용")
   → 메모리상의 엔티티 필드만 변경 (이 시점에는 DB에 아무 일도 안 일어남)

3. 트랜잭션 커밋 시점
   → JPA가 현재 엔티티 상태 vs 스냅샷을 비교
   → 달라진 필드 발견 → UPDATE SQL 자동 생성 및 전송
```

```java
@Transactional
public PostResponse updatePost(Long id, PostUpdateRequest request) {
    Post post = postRepository.findById(id)           // 1. 조회 → 영속 상태 + 스냅샷
            .orElseThrow(() -> new ResourceNotFoundException("게시글을 찾을 수 없습니다"));
    post.update(request.getTitle(), request.getContent()); // 2. 필드 변경
    return PostResponse.from(post);                        // 3. save() 없이 커밋 시 자동 UPDATE
}
```

## Q2. Dirty Checking으로 얻는 이점은?

**A:**

- **save() 누락 방지** — 영속 상태 엔티티는 필드만 바꾸면 자동 반영. save()를 잊어버려도 문제없음
- **불필요한 UPDATE 방지** — 변경이 없으면 UPDATE 쿼리를 실행하지 않음
- **깔끔한 비즈니스 로직** — Entity를 일반 자바 객체처럼 다룰 수 있어 코드가 단순해짐

## Q3. Dirty Checking이 작동하지 않는 경우는?

**A:** 엔티티가 **준영속 상태**(트랜잭션이 끝나 영속성 컨텍스트에서 분리된 상태)이면 Dirty Checking이 작동하지 않는다. 이 경우에는 `save()`를 호출해야 하며, 내부적으로 `merge()`
가 실행된다. 자세한 내용은 `docs/review/QA_save_메서드와_JPA_행동_시나리오.md` 참고.

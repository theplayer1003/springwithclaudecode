# deleteById vs delete(entity)

## Q. deleteById(id)와 delete(entity)의 차이는?

**배경:** 댓글 삭제 기능에서 삭제 전에 댓글 존재 여부와 게시글 소속을 검증하기 위해 `findById()`로 먼저 조회한다. 이 상태에서 `deleteById()`와 `delete(entity)` 중 어떤
것을 써야 하는지 의문이 생겼다.

**A:** 두 메서드의 내부 동작이 다르다.

```java
// deleteById — 내부에서 findById() + delete()를 순서대로 실행
commentRepository.deleteById(commentId);  // SELECT + DELETE (쿼리 2회)

// delete — 이미 영속 상태인 엔티티를 바로 삭제
commentRepository.

delete(comment);        // DELETE만 (쿼리 1회)
```

이미 `findById()`로 엔티티를 조회한 상태라면 `delete(entity)`가 SELECT 쿼리 1회를 아낀다. `deleteById()`를 쓰면 이미 조회한 엔티티가 있는데 내부에서 또 SELECT를
하므로 쿼리가 낭비된다.

```java
// 실제 사용 예시
Comment comment = commentRepository.findById(commentId)   // 검증을 위해 이미 조회함
                .orElseThrow(() -> new ResourceNotFoundException("댓글을 찾을 수 없습니다"));

validateBelongsToPost(comment, postId);                   // 소속 검증

commentRepository.

delete(comment);     // 이미 영속 상태 → DELETE만 실행
// commentRepository.deleteById(commentId);  // 이렇게 하면 SELECT가 또 실행됨
```

**결론:** 삭제 전에 조회/검증이 필요한 경우 `delete(entity)`, 검증 없이 바로 삭제하는 경우 `deleteById(id)`를 사용한다.

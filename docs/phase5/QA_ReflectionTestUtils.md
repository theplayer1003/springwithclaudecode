# ReflectionTestUtils와 Reflection

## Q. 단위 테스트에서 JPA 엔티티의 ID를 어떻게 설정하는가?

**배경:** `CommentServiceTest`에서 `checkPostCommentMatch(postId, comment)` 메서드를 테스트해야 했다. 이 메서드는
`comment.getPost().getId()`를 호출하는데, `new Post(...)`로 생성한 객체는 ID가 null이다. ID는 JPA가 DB 저장 시 auto-generate로 채워주는 값이므로, Mock
기반 단위 테스트에서는 DB를 거치지 않아 ID가 설정되지 않는다.

**A:** Spring이 제공하는 `ReflectionTestUtils`를 사용하면 private 필드에 값을 직접 넣을 수 있다.

```java
import org.springframework.test.util.ReflectionTestUtils;

Post post = new Post("제목", "내용", member);
ReflectionTestUtils.

setField(post, "id",1L);  // private 필드 id에 1L 설정

Comment comment = new Comment("내용", post, member);
ReflectionTestUtils.

setField(comment, "id",1L);
```

## Q. Reflection은 어떻게 private 필드에 접근할 수 있는가?

**배경:** Java에서 `private` 필드는 외부에서 접근할 수 없는 것이 원칙인데, `ReflectionTestUtils`가 이를 우회할 수 있는 이유가 궁금했다.

**A:** Reflection은 Java가 제공하는 기능으로, 런타임에 클래스의 구조(필드, 메서드, 생성자)를 조사하고 조작할 수 있는 메커니즘이다.

```java
// 일반적인 접근 — 컴파일 에러
Post post = new Post("제목", "내용", member);
post.id =1L;  // ❌ id는 private

// Reflection을 사용한 접근
Field idField = Post.class.getDeclaredField("id");  // 1. "id" 필드를 찾는다
idField.

setAccessible(true);                         // 2. private 접근 제한을 해제한다
idField.

set(post, 1L);                               // 3. 값을 넣는다
```

`ReflectionTestUtils.setField(post, "id", 1L)`이 내부적으로 하는 일이 바로 위의 3단계이다.

**주의사항:**

- Reflection은 **컴파일 타임 안전성을 포기**한다. 필드명을 문자열로 지정하므로 오타가 있어도 컴파일러가 잡지 못하고, 런타임에야 에러가 발생한다
- `private`이라는 설계 의도(캡슐화)를 무시하므로 **테스트, 프레임워크 내부 구현** 등 제한된 상황에서만 사용해야 한다
- Spring의 `@Autowired`, JPA의 필드 주입도 내부적으로 Reflection을 사용한다. 프레임워크가 private 필드에 빈을 주입할 수 있는 이유가 바로 이것이다

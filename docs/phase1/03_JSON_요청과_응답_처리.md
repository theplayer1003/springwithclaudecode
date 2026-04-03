# JSON 요청과 응답 처리

REST API에서 클라이언트와 서버는 **JSON**으로 데이터를 주고받습니다.
이 문서에서는 스프링에서 JSON이 어떻게 처리되는지를 학습합니다.

---

## 1. JSON이란?

**JSON(JavaScript Object Notation)**은 데이터를 표현하는 텍스트 형식입니다.

```json
{
    "id": 1,
    "title": "첫 번째 게시글",
    "content": "안녕하세요!",
    "viewCount": 42,
    "published": true,
    "tags": ["java", "spring"],
    "author": {
        "name": "홍길동",
        "email": "hong@example.com"
    }
}
```

자바의 객체/Map과 구조가 비슷합니다:
- `"키": 값` 형태의 쌍으로 구성
- 값의 타입: 문자열(`""`), 숫자, 불리언(`true/false`), 배열(`[]`), 객체(`{}`), `null`

왜 JSON을 쓸까요?
- 사람이 읽기 쉬움
- 거의 모든 프로그래밍 언어에서 지원
- 웹 API의 사실상 표준 형식

---

## 2. 자바 객체 ↔ JSON 변환 (직렬화/역직렬화)

스프링은 **Jackson** 라이브러리를 사용해서 자바 객체와 JSON을 자동으로 변환합니다.

### 직렬화 (Serialization) — 자바 객체 → JSON

```java
// 자바 객체
public class Post {
    private Long id;
    private String title;
    private String content;
    // getter, setter
}

Post post = new Post();
post.setId(1L);
post.setTitle("첫 번째 게시글");
post.setContent("안녕하세요!");
```

이 객체가 API 응답으로 반환되면 Jackson이 자동으로 JSON으로 변환합니다:

```json
{
    "id": 1,
    "title": "첫 번째 게시글",
    "content": "안녕하세요!"
}
```

**변환 규칙:**
- 자바 필드의 getter 메서드 이름을 기준으로 JSON 키가 결정됨
- `getId()` → `"id"`, `getTitle()` → `"title"`
- getter가 없는 필드는 JSON에 포함되지 않음

### 역직렬화 (Deserialization) — JSON → 자바 객체

클라이언트가 보낸 JSON:
```json
{
    "title": "새 게시글",
    "content": "내용입니다"
}
```

Jackson이 이 JSON을 받아서:
1. `Post` 클래스의 기본 생성자로 빈 객체 생성
2. `"title"` → `setTitle("새 게시글")` 호출
3. `"content"` → `setContent("내용입니다")` 호출
4. 완성된 객체를 컨트롤러에 전달

**중요:** 역직렬화가 동작하려면:
- 기본 생성자(매개변수 없는 생성자)가 있어야 함
- setter 메서드가 있어야 함 (또는 Jackson 설정에 따라 필드 직접 접근)

---

## 3. 응답 보내기 — 컨트롤러에서 객체 반환

### 단순 문자열 반환

```java
@GetMapping("/hello")
public String hello() {
    return "안녕하세요";
}
```

응답: `안녕하세요` (JSON이 아닌 순수 텍스트)

### 객체 반환 → 자동으로 JSON 변환

```java
@GetMapping("/posts/{id}")
public Post getPost(@PathVariable Long id) {
    Post post = new Post();
    post.setId(id);
    post.setTitle("게시글 제목");
    post.setContent("게시글 내용");
    return post;  // Jackson이 자동으로 JSON으로 변환
}
```

응답:
```json
{
    "id": 1,
    "title": "게시글 제목",
    "content": "게시글 내용"
}
```

### 리스트 반환 → JSON 배열로 변환

```java
@GetMapping("/posts")
public List<Post> getAllPosts() {
    return List.of(post1, post2, post3);
}
```

응답:
```json
[
    { "id": 1, "title": "첫 번째", "content": "..." },
    { "id": 2, "title": "두 번째", "content": "..." },
    { "id": 3, "title": "세 번째", "content": "..." }
]
```

---

## 4. 요청 받기 — @RequestBody

클라이언트가 JSON을 보내면 자바 객체로 변환받을 수 있습니다.

```java
@PostMapping("/posts")
public Post createPost(@RequestBody PostCreateRequest request) {
    // request.getTitle()  → "새 게시글"
    // request.getContent() → "내용입니다"
}
```

클라이언트 요청:
```
POST /posts
Content-Type: application/json

{
    "title": "새 게시글",
    "content": "내용입니다"
}
```

여기서 `Content-Type: application/json`은 HTTP 헤더로, "내가 보내는 데이터는 JSON이야"라고 서버에게 알려주는 역할입니다.

### 요청 객체와 응답 객체를 분리하는 이유

게시글을 **생성**할 때와 **조회**할 때 필요한 데이터가 다릅니다:

```java
// 요청용 — 클라이언트가 보내는 데이터 (id는 서버가 생성하므로 없음)
public class PostCreateRequest {
    private String title;
    private String content;
    // getter, setter
}

// 응답용 — 서버가 반환하는 데이터 (id, 생성일시 포함)
public class Post {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    // getter, setter
}
```

왜 분리할까요?
- 생성할 때 클라이언트가 `id`를 보내면 안 됨 (서버가 자동 생성)
- 조회할 때는 `id`, `createdAt` 등 추가 정보가 필요
- 보안: 클라이언트가 수정하면 안 되는 필드를 요청에서 제외

이 분리는 Phase 3에서 DTO 패턴으로 더 자세히 다룹니다. 지금은 "요청과 응답에 다른 클래스를 쓸 수 있다" 정도만 알아두세요.

---

## 5. ResponseEntity — 상태 코드와 헤더 제어

단순히 객체를 반환하면 항상 `200 OK`가 됩니다. 상태 코드를 직접 지정하고 싶을 때 `ResponseEntity`를 사용합니다.

### 기본 사용법

```java
// 200 OK (기본)
@GetMapping("/posts/{id}")
public ResponseEntity<Post> getPost(@PathVariable Long id) {
    Post post = findPost(id);
    return ResponseEntity.ok(post);
}

// 201 Created (생성 성공)
@PostMapping("/posts")
public ResponseEntity<Post> createPost(@RequestBody PostCreateRequest request) {
    Post post = savePost(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(post);
}

// 204 No Content (삭제 성공, 반환할 데이터 없음)
@DeleteMapping("/posts/{id}")
public ResponseEntity<Void> deletePost(@PathVariable Long id) {
    removePost(id);
    return ResponseEntity.noContent().build();
}

// 404 Not Found (리소스 없음)
@GetMapping("/posts/{id}")
public ResponseEntity<Post> getPost(@PathVariable Long id) {
    Post post = findPost(id);
    if (post == null) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(post);
}
```

### ResponseEntity의 구성 요소

```
ResponseEntity = HTTP 상태 코드 + HTTP 헤더 + 응답 본문(Body)
```

- `ResponseEntity<Post>` → 본문이 Post 타입의 JSON
- `ResponseEntity<Void>` → 본문이 없음
- `ResponseEntity<List<Post>>` → 본문이 Post 리스트의 JSON 배열

---

## 6. 자주 만나는 실수들

### 실수 1: getter가 없어서 JSON에 필드가 안 나옴

```java
public class Post {
    private String title;
    // getter가 없으면 Jackson이 이 필드를 JSON에 포함시키지 않음!
}
```

→ 반드시 getter를 만들어야 합니다.

### 실수 2: 기본 생성자가 없어서 역직렬화 실패

```java
public class PostCreateRequest {
    private String title;
    
    // 매개변수가 있는 생성자만 있으면
    public PostCreateRequest(String title) {
        this.title = title;
    }
    // 기본 생성자가 없어서 Jackson이 객체를 만들 수 없음!
}
```

→ 매개변수 없는 기본 생성자를 추가하거나, 별도의 생성자를 정의하지 않으면 자바가 자동으로 기본 생성자를 만들어줍니다.

### 실수 3: JSON 키 이름과 필드 이름 불일치

```java
public class Post {
    private String postTitle;  // 필드명: postTitle
    // getPostTitle() → JSON 키: "postTitle"
}
```

클라이언트가 `"title"`로 보내면 매핑되지 않습니다. 이름을 맞추거나, `@JsonProperty`로 지정할 수 있습니다:

```java
@JsonProperty("title")
private String postTitle;  // JSON에서는 "title"로 매핑
```

---

## 7. 핵심 정리

| 개념 | 설명 |
|------|------|
| **직렬화** | 자바 객체 → JSON (응답 시). getter 기준 |
| **역직렬화** | JSON → 자바 객체 (요청 시). 기본 생성자 + setter 필요 |
| **Jackson** | 스프링이 사용하는 JSON 변환 라이브러리 (자동 포함) |
| **@RequestBody** | 요청 JSON을 자바 객체로 변환 |
| **@RestController** | 반환 객체를 자동으로 JSON으로 변환 |
| **ResponseEntity** | HTTP 상태 코드 + 헤더 + 본문을 직접 제어 |

---

## 다음 단계
이 개념들을 이해하면 Phase 1의 개념 학습은 완료됩니다. 다음은 **함께 코딩** 파트로, 실제로 메모리 기반 게시글 CRUD API를 작성합니다.

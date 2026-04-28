# HttpServletRequest와 @RequestBody의 관계

## Q. HttpServletRequest를 감싸서 RequestBody가 되는 건가?

**A:** 아니다. `HttpServletRequest`는 요청 **전체**(URL, 헤더, 본문 등)를 담은 객체이고, `@RequestBody`는 그 안의 **본문(body) 부분만** 꺼내서 DTO로 역직렬화하는 것이다. 감싸는 게 아니라 안에서 일부를 추출하는 구조다.

### 전체 흐름

```
클라이언트 HTTP 요청
    ↓
톰캣: 요청 전체를 HttpServletRequest 객체로 생성 (JSON은 body에 문자열 그대로)
    ↓
Filter: request.getHeader()로 헤더의 토큰만 확인. body는 안 건드림
    ↓
Controller: @RequestBody가 body의 JSON을 꺼내 Jackson으로 DTO에 역직렬화
```

`HttpServletRequest`는 처음부터 끝까지 하나의 객체이고, 각 단계에서 필요한 부분만 꺼내 쓴다. Filter는 헤더를, Controller는 body를 꺼내 쓴다.

### HttpServletRequest 내부 구조

```
HttpServletRequest 객체 안에는:
├── 메서드: POST
├── URL: /posts
├── 헤더: Authorization: Bearer xxx
│         Content-Type: application/json
└── 본문(body): {"title": "제목", "content": "내용"}  ← JSON 문자열 그대로
```

### @RequestBody가 하는 일

Controller에 도달하면 Spring이 HttpServletRequest의 본문(body)에서 JSON 문자열을 꺼내 Jackson으로 역직렬화한다.

```
HttpServletRequest의 body
  {"title": "제목", "content": "내용"}    ← JSON 문자열
              ↓ Jackson 역직렬화
  PostCreateRequest 객체
    title = "제목"
    content = "내용"
```

---

## Q. 톰캣이 HttpServletRequest를 만드는 것도 역직렬화인가?

**A:** 아니다. 톰캣이 하는 일과 @RequestBody가 하는 일은 다르다.

| 단계 | 하는 일 | 용어 |
|------|---------|------|
| 톰캣 | HTTP 요청(텍스트 프로토콜)을 Java 객체로 **래핑** | 래핑/파싱 |
| @RequestBody | JSON 문자열을 Java DTO 객체로 **변환** | 역직렬화 |

톰캣은 HTTP 요청의 각 부분(메서드, URL, 헤더, 본문)을 HttpServletRequest라는 그릇에 담아두는 것이다. 본문의 JSON을 해석하거나 Java 객체로 변환하지 않고, 문자열 그대로 보관한다.

역직렬화는 `{"title": "제목"}` 이라는 JSON 문자열이 PostCreateRequest 객체의 title 필드에 "제목"이 들어가는 것처럼, 데이터 형식을 해석해서 Java 객체의 필드에 매핑하는 작업을 말한다. 이건 Controller 단계에서 Jackson이 하는 일이다.

### 정리

- **직렬화**: Java 객체 → JSON (응답 시, Jackson)
- **역직렬화**: JSON → Java 객체 (요청 시, @RequestBody + Jackson)
- **톰캣**: HTTP 텍스트 → HttpServletRequest (직렬화/역직렬화가 아닌 **래핑**)

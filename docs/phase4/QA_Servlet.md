# Servlet

## Q. 필터의 파라미터에 등장하는 Servlet이란?

**배경:** JwtAuthenticationFilter를 작성할 때 메서드 파라미터에 `HttpServletRequest`, `HttpServletResponse`가 등장했다. Controller에서는 본 적 없는
타입이라 의문이 생겼다.

**A:** Servlet은 Java에서 HTTP 요청을 처리하는 기본 단위다. 톰캣(서버)이 HTTP 요청을 받으면 Java가 이해할 수 있는 객체로 변환하는데, 그것이 `HttpServletRequest`(요청
정보)와 `HttpServletResponse`(응답 정보)다.

```
클라이언트 HTTP 요청
    ↓
[톰캣] → HttpServletRequest, HttpServletResponse 객체 생성
    ↓
[Spring Security 필터] ← Servlet 객체를 직접 다룸
    ↓
[DispatcherServlet → Controller] ← Spring이 Servlet을 감싸서 @RequestBody 등 편의 기능 제공
```

Controller에서 `@RequestBody`, `@PathVariable` 같은 편의 기능을 쓸 수 있는 이유는 Spring MVC가 Servlet 객체를 감싸서 제공하기 때문이다. 하지만 Security
필터는 Controller보다 앞 단계(Spring MVC 밖)에서 실행되므로 이 편의 기능이 적용되기 전이다.

```java
// Filter — Servlet 객체를 직접 다뤄야 함
String token = request.getHeader("Authorization");    // HttpServletRequest에서 직접 추출
response.

setStatus(HttpServletResponse.SC_UNAUTHORIZED); // HttpServletResponse에 직접 작성

// Controller — Spring이 변환해줌
@PostMapping("/posts")
public ResponseEntity<PostResponse> create(@RequestBody PostCreateRequest request) {
    // JSON → DTO 변환을 Spring이 알아서 처리
}
```

**핵심:** Filter와 Controller는 같은 `HttpServletRequest`/`HttpServletResponse` 객체를 다루지만, Controller에서는 Spring MVC가 편의 기능으로
감싸주고, Filter에서는 직접 다뤄야 한다.

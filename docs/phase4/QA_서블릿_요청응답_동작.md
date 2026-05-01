# 서블릿의 요청/응답 동작 방식

## Q. Filter에서 response에 값을 쓰고 return만 하면 클라이언트에 응답이 가는 이유는?

**A:** `request`와 `response` 객체는 우리가 만든 게 아니라 **톰캣(서블릿 컨테이너)이 만들어서 넘겨준 것**이다.

```
클라이언트 → HTTP 요청 → 톰캣
                         ↓
                    request 객체 생성 (요청 정보 담김)
                    response 객체 생성 (빈 상태)
                         ↓
                    Filter/Controller에 둘 다 넘김
                         ↓
                    코드가 response에 내용을 채움
                         ↓
                    톰캣이 response를 HTTP 응답으로 변환해서 클라이언트에 전송
```

`response`는 톰캣이 관리하는 객체이므로, 우리는 "이걸 담아서 보내줘"라고 쓰기만 하면 된다. 반환값으로 돌려주는 것이 아니라, 톰캣이 알아서 `response` 객체의 내용을 클라이언트에 전송한다.

---

## Q. Controller의 ResponseEntity와 Filter의 response 직접 작성은 뭐가 다른가?

**A:** 같은 일을 한다. 추상화 수준만 다르다.

```java
// Controller 방식 — Spring이 response 변환을 대신 해줌
return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);

// Filter 방식 — 직접 response에 씀 (Spring MVC 밖이라 ResponseEntity 사용 불가)
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
response.setContentType("application/json");
response.getWriter().write("{...}");
```

Controller의 `ResponseEntity`도 결국 Spring이 내부적으로 `response.setStatus()`, `response.getWriter().write()` 등을 호출한다. Filter에서는
Spring MVC 계층 밖이라 직접 해야 할 뿐이다.

---

## Q. Filter에서 return과 filterChain.doFilter()의 차이는?

**A:**

```java
catch (ExpiredJwtException e) {
    response.setStatus(401);
    response.getWriter().write("...");
    return;  // filterChain.doFilter()를 호출하지 않고 여기서 끝
}

filterChain.doFilter(request, response);  // 정상일 때만 다음으로 진행
```

- **`return`**: "이 필터에서 처리 끝. 다음 필터/Controller로 보내지 않겠다." → 톰캣이 현재 `response` 내용을 그대로 클라이언트에 전송
- **`filterChain.doFilter()`**: "다음 필터로 넘긴다." → 최종적으로 Controller까지 도달

---

## Q. GlobalExceptionHandler로 Filter의 예외를 처리할 수 없는 이유는?

**A:** `@RestControllerAdvice`는 Controller 레벨의 예외만 잡는다. Filter는 Controller보다 앞단에서 동작하므로, Filter에서 발생한 예외는
`@RestControllerAdvice`의 범위 밖이다.

```
요청 → Filter(여기서 예외 발생) → Controller
         ↑                         ↑
    이 레벨의 예외는             @RestControllerAdvice가
    직접 response에 써야 함      잡을 수 있는 범위
```

따라서 Filter에서 발생하는 예외(토큰 만료 등)는 `HttpServletResponse`에 직접 응답을 작성해야 한다.

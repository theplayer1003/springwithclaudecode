# Servlet

## Q. 필터의 파라미터에 등장하는 Servlet이란?

**A:** Servlet은 Java에서 HTTP 요청을 처리하는 기본 단위다. 톰캣(서버)이 HTTP 요청을 받으면 Java가 이해할 수 있는 객체로 변환하는데, 그것이 `HttpServletRequest`(요청
정보)와 `HttpServletResponse`(응답 정보)다.

```
클라이언트 HTTP 요청
    ↓
[톰캣] → HttpServletRequest, HttpServletResponse 객체 생성
    ↓
[Spring Security 필터] ← Servlet 객체를 직접 다룸
    ↓
[Controller] ← Spring이 Servlet을 감싸서 @RequestBody 등 편의 기능 제공
```

Controller에서 `@RequestBody`, `@PathVariable` 같은 편의 기능을 쓸 수 있는 이유는 Spring이 Servlet을 감싸서 제공하기 때문이다. 하지만 Security 필터는
Controller보다 앞 단계에서 실행되므로 Spring의 편의 기능이 적용되기 전이라 `request.getHeader("Authorization")`처럼 Servlet 객체를 직접 다뤄야 한다.

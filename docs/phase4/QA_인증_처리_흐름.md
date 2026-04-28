# 인증 처리 흐름

## Q. 컨트롤러에서 SecurityContext에 이미 인증 정보가 있는데, 토큰 검증은 언제 어디서 이루어지나?

**A:** 컨트롤러에 도달한 시점에는 **이미 인증이 끝나 있다.** 그 일을 하는 것이 `JwtAuthenticationFilter`다.

### 요청 처리 흐름

```
클라이언트 요청 (Authorization: Bearer xxx)
        ↓
[Servlet 컨테이너]
        ↓
[Security Filter Chain] ← 여기서 인증/인가 처리
    ├─ JwtAuthenticationFilter        ← 토큰 검증 & SecurityContext에 저장
    ├─ ... (기타 시큐리티 필터들)
    └─ FilterSecurityInterceptor      ← 인가(authorizeHttpRequests) 체크
        ↓
[DispatcherServlet]
        ↓
[Controller] ← 이 시점엔 이미 SecurityContext에 인증 정보 존재
```

---

## Q. JwtAuthenticationFilter가 매 요청마다 하는 일은?

**A:** 아래 코드는 `OncePerRequestFilter`를 상속받아 **매 요청마다 한 번씩** 실행된다.

```java
String token = resolveToken(request);                          // 1. 헤더에서 토큰 추출

if (token != null && jwtTokenProvider.validateToken(token)) {  // 2. 토큰 유효성 검증
    String username = jwtTokenProvider.getUsername(token);     // 3. 토큰에서 정보 추출
    String role = jwtTokenProvider.getRole(token);

    UsernamePasswordAuthenticationToken authentication = ...;  // 4. 인증 객체 생성

    SecurityContextHolder.getContext().setAuthentication(...); // 5. 컨텍스트에 저장
}

filterChain.doFilter(request, response);                       // 6. 다음 필터로
```

토큰이 유효하면 SecurityContext에 인증 정보를 채우고, 유효하지 않거나 없으면 아무것도 하지 않는다 (컨텍스트가 비어 있는 상태로 다음 필터로 넘어감).

---

## Q. 그러면 인가(Authorization)는 어디서 처리되나?

**A:** SecurityConfig의 `authorizeHttpRequests` 규칙을 기반으로 `FilterSecurityInterceptor`가 처리한다. 이 필터는 Security Filter Chain의
마지막쯤에 위치한다.

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/posts/**").permitAll()
    .anyRequest().authenticated()  // 나머지는 인증 필요
)
```

- SecurityContext가 비어 있는데 `authenticated()` 경로 요청 → 403 반환
- SecurityContext에 인증 정보 있음 → 통과 → 컨트롤러 도달

---

## Q. 인증, 인가, 비즈니스 로직의 책임 분리를 정리하면?

**A:**

| 단계                 | 담당                          | 하는 일                             |
|--------------------|-----------------------------|----------------------------------|
| 인증(Authentication) | `JwtAuthenticationFilter`   | 토큰 검증 → SecurityContext 채우기      |
| 인가(Authorization)  | `FilterSecurityInterceptor` | SecurityContext 확인 → 경로 접근 허용/거부 |
| 비즈니스 로직            | `Controller`                | 이미 인증/인가 완료된 상태에서 동작             |

그래서 컨트롤러에서는 "토큰 처리"를 걱정할 필요 없이 `SecurityContextHolder` 또는 `@AuthenticationPrincipal`로 **결과물만 꺼내 쓰면** 된다.

이것이 Spring Security의 장점이다 — 인증/인가가 **필터 레이어에서 처리**되므로 비즈니스 로직과 완전히 분리된다.

---

## Q. 요청 전체 시나리오를 한 줄로 요약하면?

**A:** 클라이언트 요청에 생성하고자 하는 리소스 정보와 함께 토큰이 오면, 이 요청이 컨트롤러에 도달하기 전 Spring Security 필터 체인이 토큰 유효성을 검증하고 정보를 추출해 "인증되었음"을
SecurityContext에 등록한다. 그 후 인가 규칙을 통과한 요청만 컨트롤러에 도달하며, 컨트롤러는 SecurityContext에서 인증 결과물만 꺼내 비즈니스 로직을 수행한다.

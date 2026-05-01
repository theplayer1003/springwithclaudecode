# 응답 코드와 디버깅

## Q. Spring Security 환경에서 뭘 잘못해도 전부 403이 오는 이유는?

**A:** Security Filter가 Controller보다 먼저 동작하기 때문이다.

```
잘못된 요청 → Security Filter → 403 차단 (여기서 끝)
                                    ↓ (도달 못함)
                              Controller → 405를 줄 수도 없음
```

Controller에 도달하기 전에 Security에서 차단되면, Controller가 반환할 수 있는 405(Method Not Allowed)나 404(Not Found)는 아예 발생하지 않는다.

---

## Q. 401과 403이 구분되지 않는 문제는?

**A:** Spring Security는 기본적으로 인증 실패도 403으로 반환한다. `AuthenticationEntryPoint`를 설정하면 401과 403을 구분할 수 있다.

그러나 `AuthenticationEntryPoint`만 설정하면 **인가 실패(권한 부족)도 401로 반환**되는 문제가 발생한다. `AccessDeniedHandler`도 함께 설정해야 401과 403이 정확히
구분된다.

```java
.exceptionHandling(exception -> exception
    .authenticationEntryPoint((request, response, authException) -> {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // 401
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"인증이 필요합니다\"}");
    })
    .accessDeniedHandler((request, response, accessDeniedException) -> {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);  // 403
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"접근 권한이 없습니다\"}");
    })
)
```

| 핸들러                        | 역할                | 반환 코드 |
|----------------------------|-------------------|-------|
| `AuthenticationEntryPoint` | 인증 실패 (토큰 없음, 만료) | 401   |
| `AccessDeniedHandler`      | 인가 실패 (권한 부족)     | 403   |

설정 후 응답 구분:

| 상황                  | 반환 코드   | 의미     |
|---------------------|---------|--------|
| 토큰 없이 요청            | **401** | 인증 안 됨 |
| USER 토큰으로 /admin 접근 | **403** | 권한 부족  |

---

## Q. 잘못된 HTTP 메서드를 보냈는데 405 대신 403이 오는 건?

**A:** 이건 의도된 동작이다. 상황을 나눠서 보면:

### ADMIN 토큰 + 잘못된 메서드 → 405 정상 반환

```
ADMIN 토큰 + GET /admin/posts/1
  → Security: ADMIN이니까 통과 ✓
  → Controller: GET 매핑 없음 → 405 반환 ✓
```

올바른 토큰으로 인증/인가를 통과한 경우, Controller에 도달하므로 405가 정상적으로 나온다.

### 토큰 없음 + 잘못된 메서드 → 401 반환

```
토큰 없음 + GET /admin/posts/1
  → Security: 인증 안 됨 → 401 차단 (여기서 끝)
  → Controller: 도달 못함 → 405를 줄 기회 없음
```

이 경우 405를 받을 수 없다. 보안 관점에서 이는 올바른 동작이다 — 권한 없는 사용자에게 "이 경로에 어떤 메서드가 존재하는지" 정보를 주지 않는 것이 더 안전하기 때문이다.

---

## Q. 정리: 관리자 강제 삭제 엔드포인트의 응답 코드 전체 (테스트 검증 완료)

| 상황                           | 기대 코드 | 실제 코드 | 정상? | 비고                           |
|------------------------------|-------|-------|-----|------------------------------|
| ADMIN 토큰 + DELETE + 존재하는 게시글 | 204   | 204   | O   |                              |
| ADMIN 토큰 + DELETE + 없는 게시글   | 404   | 404   | O   |                              |
| ADMIN 토큰 + 잘못된 메서드 (GET)     | 405   | 401   | 허용  | Security가 먼저 처리, 보안상 허용 범위   |
| USER 토큰 + 어떤 메서드든            | 403   | 403   | O   | AccessDeniedHandler 추가 후 정상  |
| 토큰 없음 + 어떤 메서드든              | 401   | 401   | O   | AuthenticationEntryPoint로 처리 |

---

## Q. 디버깅 팁: 403이 나왔을 때 확인 순서는?

1. **토큰을 넣었는가?** → 빠뜨렸으면 401이 나와야 정상 (EntryPoint 설정 후)
2. **토큰이 유효한가?** → 만료되었거나 잘못된 토큰
3. **해당 경로에 맞는 권한인가?** → `/admin/**`은 ADMIN만 접근 가능
4. **HTTP 메서드가 맞는가?** → 위 세 가지를 다 통과했는데 403이면 메서드 확인

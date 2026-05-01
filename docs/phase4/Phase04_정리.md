# Phase 4: 인증과 인가

---

## 1. 인증과 인가의 개념

인증과 인가는 모두 보안 관련 개념이지만 약간 다른 의미를 가진다.
인증은 요청자의 신원을 확인하는 작업이다.
인가는 인증된 요청자가 어디까지 권한을 가지고 있는지 확인하는 작업이다.
인증은 인가보다 앞선 작업이며, 인증을 받았다고 해서 모두가 같은 인가를 받을 수 있는 것은 아니다.

---

## 2. 세션 방식과 토큰 방식

웹은 HTTP 프로토콜의 무상태 특성을 가진다. 무상태란 서버에 상태값을 저장하지 않는다는 뜻으로, 서버는 요청에 대한 처리를 반환 후 관련 정보를 기억해두지 않는다.
따라서 요청자의 신원(인증)을 확인하기 위해서는 무상태성을 깨거나(세션 방식) 요청자가 매 요청마다 자신을 인증할 수단을 포함해야 한다(토큰 방식).

### 세션 방식

요청자가 인증에 성공하면 세션을 만들어 서버 메모리에 저장해두고, 해당 세션의 ID를 쿠키로 전달한다. 이후 요청자가 모든 요청에 쿠키를 포함시키면 인증을 할 수 있게 된다.

- **장점**: 구현이 간단하고, 상태를 서버가 가지기 때문에 서버에 강제력이 생긴다. 서버가 세션을 취소시키면 즉시 인증이 무효화된다.
- **단점**: HTTP의 무상태성이 깨진다. 서버를 늘릴 때 모든 서버가 세션 상태를 공유해야 하고, 상태를 기억해야 하므로 리소스가 늘어난다.

### 토큰 방식

요청자가 인증에 성공하면 서버는 토큰을 발급한다. 요청자는 이후 모든 요청의 헤더에 토큰을 포함시키고, 서버는 토큰을 검증해서 요청자를 인증한다.

- **장점**: 서버가 무상태를 유지할 수 있어 확장이 쉽고, 장애에 강하다.
- **단점**: 토큰 탈취에 취약하다. 자신을 인증할 수단을 요청자가 가지게 되는데, 탈취당하면 서버가 상태를 가지지 않기 때문에 강제 무효화가 어렵다.

---

## 3. JWT 구조

현대 웹 애플리케이션에서 표준적으로 쓰이는 토큰은 JWT(JSON Web Token)이다. JWT는 세 부분으로 이루어진다.

```
Header.Payload.Signature
```

- **Header**: 토큰의 메타 정보 (알고리즘, 타입)
- **Payload**: 실제 담고자 하는 요청에 대한 정보 (Claims)
- **Signature**: 위변조 방지 서명

이것들은 모두 암호화가 아닌 Base64 인코딩이기 때문에 JWT에는 보안이 중요한 사항(비밀번호 등)을 담을 수 없다.

Claims는 JWT의 Payload에 담기는 정보 하나하나를 지칭하는 말로, JSON의 키:밸류 쌍 같은 개념이다.

---

## 4. JWT 구현

### 라이브러리 의존성

JWT는 Spring에 기본 내장되어 있지 않으므로 라이브러리 의존성을 추가해야 한다.

- `jjwt-api`: JWT 생성, 파싱을 위한 인터페이스
- `jjwt-impl`: 실제 구현체. 런타임에만 필요하므로 `runtimeOnly`
- `jjwt-jackson`: JWT의 JSON 처리를 위한 Jackson. Spring 기본 Jackson과 별개. `runtimeOnly`

### JwtTokenProvider

토큰의 생성과 검증, 정보 추출을 담당하는 클래스이다.

- 토큰 생성을 위해 비밀 키와 만료 시간 값이 필요하며, `application.properties`에서 읽어온다
- 사용자 토큰에서 Username, Role 등을 추출하는 메서드를 제공한다
- `validateToken`에서 만료된 토큰은 `ExpiredJwtException`을 밖으로 던져 Filter에서 별도 처리할 수 있게 한다

### JwtAuthenticationFilter

매 요청마다 토큰을 검증하는 필터이다. `OncePerRequestFilter`를 상속해 `doFilterInternal` 메서드를 구현한다.

- 요청 헤더에서 토큰을 추출
- 토큰이 유효하면 SecurityContext에 인증 정보 저장
- 토큰이 만료되었으면 `HttpServletResponse`에 직접 401 응답 작성 후 `return`

`OncePerRequestFilter`는 요청 하나당 정확히 한 번만 필터가 실행됨을 보장한다. 일반 `Filter`를 상속하면 내부 포워딩 등으로 같은 요청에서 여러 번 필터가 실행될 수 있다.

---

## 5. Spring Security 구조

Spring Security는 Spring에서 제공하는 보안 프레임워크이다. 핵심은 **필터 체인**이다.

### 필터 체인의 동작 순서

```
클라이언트 요청
    ↓
1. 보안 컨텍스트 초기화
2. CORS 처리
3. 인증 처리 → JWT 토큰 검증 (JwtAuthenticationFilter)
4. 인가 처리 → 요청한 엔드포인트에 접근 권한이 있는지 체크
5. 예외 처리
    ↓
Controller → 여기까지 도달하면 인증, 인가가 통과되었다는 뜻
```

중간에 인증 실패나 권한 부족이 발견되면 Controller에 도달하지 못하고 에러 응답을 반환한다. Controller에 도달하지 못하기 때문에 `GlobalExceptionHandler`(
`@RestControllerAdvice`)를 사용할 수 없다. 인증/인가 오류는 `HttpServletResponse`에 직접 응답을 작성하여 처리한다.

### SecurityContext

인증/인가를 무사히 통과하면 통과한 정보가 SecurityContext에 저장된다. `Authentication` 객체로 생성되어 저장되며, Controller는 SecurityContext에서 사용자 정보를 꺼내
요청을 처리한다. 요청이 완료되면 SecurityContext는 곧바로 비워진다. 무상태이기 때문에 다음 요청에도 토큰이 지참될 것이며, 똑같은 과정을 거쳐 요청을 처리하게 된다.

`Authentication` 객체에는 다음 정보가 담겨 있다:

- **Principal**: 사용자 식별 정보 (username 등)
- **Credentials**: 인증 수단 (비밀번호, 토큰 등)
- **Authorities**: 권한 목록 (ROLE_USER, ROLE_ADMIN 등)

### SecurityConfig 설정

`SecurityFilterChain`을 `SecurityConfig`에 설정한다. `HttpSecurity`에서 제공하는 메서드는 약 30~40개 정도 되지만, 관심사의 분리가 잘 되어 있어 모두 알 필요는 없다.
인증 방식에 따라 사용하는 메서드가 그룹화 되어 있다.

- 일반 웹 사이트: `formLogin`, `logout`, `rememberMe`, `csrf`, `sessionManagement`
- REST API(JWT): `csrf`, `sessionManagement(STATELESS)`, `addFilterBefore`, `cors`
- 소셜 로그인: `oauth2Login`, `oauth2ResourceServer`

계층 구조로 이해하면:

- 요청 제어: `authorizeHttpRequests`, `securityMatcher`
- 인증 방식: `formLogin`, `httpBasic`, `oauth2Login`, `saml2Login`
- 취약점 방어: `csrf`, `headers`, `cors`
- 세션/예외: `sessionManagement`, `exceptionHandling`, `anonymous`
- 기타 확장: `addFilter...`, `userDetailsService`, `requestCache`

---

## 6. 비밀번호 암호화

비밀번호는 DB에 그대로 저장되어선 안 된다. DB가 해킹당하면 사용자의 비밀번호가 그대로 노출되기 때문이다. Spring Security는 `BCryptPasswordEncoder`를 제공한다.

- **단방향 해시**: 원문을 해시 함수를 거쳐 다른 형태로 바꿔준다. 해시 결과를 통해 원문을 알 수 없다.
- **솔트**: 사용자의 비밀번호에 랜덤한 값을 섞는 기법이다. 여러 사용자가 같은 비밀번호를 사용하더라도 솔트 값 때문에 모두 해싱 결과가 다르게 나온다.

---

## 7. 역할 기반 인가

역할에 따라 서비스의 접근 범위를 지정하는 것이 '인가'이다.

### URL 기반 인가

SecurityConfig에서 경로별 접근 권한을 설정한다.

```java
.authorizeHttpRequests(auth ->auth
        .

requestMatchers("/admin/**").

hasRole("ADMIN")
    .

anyRequest().

authenticated()
)
```

`/admin/**` 경로는 ADMIN 역할을 가진 사용자만 접근 가능하다. 일반 사용자가 접근하면 403, 인증되지 않은 사용자가 접근하면 401을 반환한다.

### 401과 403 구분

기본 Spring Security는 인증 실패와 인가 실패를 모두 403으로 반환한다. 이를 구분하려면 두 가지 핸들러를 설정해야 한다.

- **AuthenticationEntryPoint**: 인증 실패 (토큰 없음, 만료) → 401
- **AccessDeniedHandler**: 인가 실패 (권한 부족) → 403

---

## 8. 작성자 권한 검증

### 엔티티 연관관계

게시글(Post)과 회원(Member)은 N:1 관계이다. Post에 `@ManyToOne`으로 Member를 연결하고, 게시글 생성 시 로그인한 회원을 작성자로 저장한다. Member에서 Post로의 양방향 관계(
`@OneToMany`)는 불필요하므로 설정하지 않았다.

### 검증 흐름

1. Controller에서 `@AuthenticationPrincipal String username`으로 현재 로그인한 사용자의 username을 추출
2. Service에서 게시글의 작성자(`post.getAuthor()`)와 요청자 username을 비교
3. 불일치 시 `UnauthorizedAccessException`을 던져 403 응답

이 패턴은 게시글과 댓글 모두에 동일하게 적용되었다.

---

## 9. Phase 4에서 배운 것 요약

- 인증(Authentication)과 인가(Authorization)의 차이와 각각의 구현 방식
- 세션 방식과 토큰 방식의 장단점, JWT의 구조와 동작 원리
- Spring Security의 필터 체인 구조와 요청 처리 흐름
- SecurityConfig를 통한 URL 기반 인가 설정
- JWT 토큰 생성, 검증, 만료 처리
- 비밀번호 암호화 (BCrypt, 단방향 해시, 솔트)
- 엔티티 연관관계에서 양방향 필요성 판단 기준
- Filter 레벨과 Controller 레벨의 예외 처리 차이
- `HttpServletResponse`를 통한 직접 응답 작성
- 역할(ADMIN/USER) 기반 접근 제어와 401/403 구분

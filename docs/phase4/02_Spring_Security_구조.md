# Spring Security 구조

---

## 1. Spring Security란

Spring Security는 인증과 인가를 처리하는 스프링의 보안 프레임워크입니다. 직접 구현하면 복잡하고 보안 취약점이 생기기 쉬운 부분을 프레임워크가 대신 처리해줍니다.

---

## 2. 필터 체인 (Filter Chain)

Spring Security의 핵심은 **필터 체인**입니다. 클라이언트의 HTTP 요청이 Controller에 도달하기 전에 여러 개의 필터를 거칩니다.

```
클라이언트 요청
    ↓
[Filter 1] 보안 컨텍스트 초기화
    ↓
[Filter 2] CORS 처리
    ↓
[Filter 3] 인증 처리 ← JWT 토큰 검증은 여기서
    ↓
[Filter 4] 인가 처리 (이 URL에 접근 권한이 있는가?)
    ↓
[Filter 5] 예외 처리
    ↓
Controller (여기까지 도달하면 인증/인가 통과)
```

필터는 순서대로 실행됩니다. 중간에 인증 실패나 권한 부족이 발견되면 Controller에 도달하지 않고 바로 에러 응답을 반환합니다.

Phase 3에서 배운 `@RestControllerAdvice`는 Controller 안에서 발생하는 예외를 처리했습니다. 하지만 Security 필터에서 발생하는 예외는 Controller에 도달하기 전이므로 별도의 처리가 필요합니다. 이 부분은 구현할 때 다시 설명합니다.

---

## 3. SecurityContext와 Authentication

인증이 성공하면 사용자 정보가 **SecurityContext**에 저장됩니다.

```
[JWT 필터]
토큰 검증 성공 → Authentication 객체 생성 → SecurityContext에 저장
    ↓
[Controller]
SecurityContext에서 현재 사용자 정보를 꺼내 사용
    ↓
[요청 완료]
SecurityContext 비워짐 (다음 요청에는 다시 토큰으로 인증해야 함)
```

**Authentication** 객체에는 다음 정보가 들어있습니다:
- **Principal** — 사용자 식별 정보 (username 등)
- **Credentials** — 인증 수단 (비밀번호, 토큰 등. 인증 후에는 보안상 비워짐)
- **Authorities** — 권한 목록 (ROLE_USER, ROLE_ADMIN 등)

SecurityContext는 **요청 하나의 생명주기** 동안만 유지됩니다. 요청이 끝나면 사라지고, 다음 요청에서는 JWT 토큰으로 다시 인증해야 합니다. 이것이 무상태(Stateless)입니다.

---

## 4. SecurityFilterChain 설정

어떤 URL에 어떤 보안 규칙을 적용할지를 **SecurityFilterChain**에서 설정합니다.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())       // REST API이므로 CSRF 비활성화
            .sessionManagement(session ->        // 세션 사용하지 않음 (JWT 사용)
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()      // 회원가입/로그인은 누구나
                .requestMatchers(HttpMethod.GET, "/posts/**").permitAll()  // 조회는 누구나
                .anyRequest().authenticated()                 // 나머지는 인증 필요
            );

        return http.build();
    }
}
```

### 주요 설정 설명

**CSRF 비활성화**

CSRF(Cross-Site Request Forgery)는 브라우저의 쿠키를 이용한 공격을 방어하는 기능입니다. 우리는 쿠키 대신 JWT 토큰을 사용하므로 CSRF 방어가 필요 없습니다.

**세션 비활성화**

JWT 기반 인증이므로 서버에 세션을 생성하지 않도록 `STATELESS`로 설정합니다.

**URL별 접근 규칙**

`authorizeHttpRequests`에서 URL 패턴별로 접근 권한을 정의합니다:
- `permitAll()` — 인증 없이 누구나 접근 가능
- `authenticated()` — 인증된 사용자만 접근 가능
- `hasRole("ADMIN")` — 특정 역할을 가진 사용자만 접근 가능

규칙은 **위에서 아래로 순서대로** 평가됩니다. 더 구체적인 규칙을 위에 놓아야 합니다.

---

## 5. 비밀번호 암호화 (PasswordEncoder)

비밀번호를 DB에 그대로 저장하면 안 됩니다. DB가 해킹당하면 모든 사용자의 비밀번호가 노출됩니다.

```
사용자 입력: "mypassword123"
                ↓ 암호화
DB 저장: "$2a$10$N9qo8uLOickgx2ZMRZoMye..."  ← 원래 비밀번호를 알 수 없음
```

Spring Security는 `BCryptPasswordEncoder`를 제공합니다:
- **단방향 해시** — 암호화는 가능하지만 복호화(원래 값으로 되돌리기)는 불가능
- **솔트(Salt)** — 같은 비밀번호여도 매번 다른 결과가 나옴. "1234"를 10명이 쓰더라도 DB에는 전부 다른 값으로 저장됨

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// 회원가입 시
String encoded = passwordEncoder.encode("mypassword123");  // 암호화

// 로그인 시
passwordEncoder.matches("mypassword123", encoded);  // true (입력값과 저장된 해시 비교)
```

---

## 6. 전체 구조 정리

```
[클라이언트 요청]
  ↓
[SecurityFilterChain]
  ├── JWT 필터: 토큰 검증 → Authentication 생성 → SecurityContext에 저장
  ├── 인가 필터: URL 규칙에 따라 접근 허용/거부
  └── 실패 시 에러 응답 반환
  ↓
[Controller]
  SecurityContext에서 현재 사용자 정보 사용
  ↓
[Service]
  비즈니스 로직 (작성자 확인 등)
```

---

## 7. 핵심 정리

| 개념 | 설명 |
|------|------|
| **필터 체인** | 요청이 Controller에 도달하기 전에 거치는 보안 필터들의 체인 |
| **SecurityContext** | 인증된 사용자 정보를 저장하는 공간. 요청 하나의 생명주기 동안 유지 |
| **Authentication** | 사용자 식별 정보 + 권한 목록을 담는 객체 |
| **SecurityFilterChain** | URL별 보안 규칙을 설정하는 곳 |
| **PasswordEncoder** | 비밀번호를 단방향 해시로 암호화. BCrypt 사용 |
| **CSRF 비활성화** | JWT 기반 API에서는 CSRF 방어 불필요 |
| **STATELESS** | 서버에 세션을 생성하지 않음. JWT로 매 요청 인증 |

---

## 다음 문서

다음 문서에서는 **JWT 토큰의 생성, 검증, 파싱을 담당하는 유틸리티 클래스**와 **JWT 인증 필터**의 구현 방법을 설명합니다.

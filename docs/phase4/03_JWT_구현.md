# JWT 구현

---

## 1. 필요한 의존성

JWT 관련 기능은 Spring에 내장되어 있지 않습니다. 별도 라이브러리가 필요합니다.

```kotlin
// build.gradle.kts
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

- `jjwt-api` — JWT 생성/파싱을 위한 인터페이스
- `jjwt-impl` — 실제 구현체 (런타임에만 필요)
- `jjwt-jackson` — JWT의 JSON 처리를 Jackson으로 수행 (런타임에만 필요)

`implementation`과 `runtimeOnly`의 차이:

- `implementation` — 컴파일 시점 + 런타임 모두 필요 (코드에서 직접 import)
- `runtimeOnly` — 런타임에만 필요 (코드에서 직접 사용하지 않지만 실행 시 필요)

---

## 2. JwtTokenProvider — 토큰 생성과 검증

JWT 토큰의 생성, 검증, 정보 추출을 담당하는 클래스입니다.

### 토큰 생성

```java

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")       // application.properties에서 비밀키 읽기
    private String secretKey;

    @Value("${jwt.expiration}")   // 만료 시간 (밀리초)
    private long expiration;

    // 비밀키를 Key 객체로 변환
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // 토큰 생성
    public String createToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)                         // 사용자 식별자
                .claim("role", role)                       // 커스텀 데이터 (역할)
                .issuedAt(now)                             // 발행 시간
                .expiration(expiryDate)                    // 만료 시간
                .signWith(getSigningKey())                 // 비밀키로 서명
                .compact();                                // 문자열로 변환
    }
}
```

`@Value`는 `application.properties`의 값을 주입합니다:

```properties
# application.properties
jwt.secret=이곳에충분히긴비밀키를넣습니다최소32바이트이상이어야합니다
jwt.expiration=3600000   # 1시간 (밀리초)
```

비밀키를 코드에 직접 쓰지 않고 설정 파일에서 읽는 이유:

- 환경(dev/prod)별로 다른 키를 사용할 수 있음
- 코드가 공개되어도 비밀키가 노출되지 않음 (설정 파일은 .gitignore로 제외 가능)

### 토큰에서 정보 추출

```java
// 토큰에서 username 추출
public String getUsername(String token) {
    return getClaims(token).getSubject();
}

// 토큰에서 role 추출
public String getRole(String token) {
    return getClaims(token).get("role", String.class);
}

// 토큰의 Claims(Payload) 파싱
private Claims getClaims(String token) {
    return Jwts.parser()
            .verifyWith(getSigningKey())    // 서명 검증에 사용할 키
            .build()
            .parseSignedClaims(token)       // 토큰 파싱 + 서명 검증
            .getPayload();                  // Payload(Claims) 반환
}
```

`parseSignedClaims`는 두 가지를 동시에 수행합니다:

1. 서명 검증 — 토큰이 위변조되지 않았는지 확인
2. 만료 확인 — 토큰이 만료되었으면 예외 발생

### 토큰 유효성 검증

```java
public boolean validateToken(String token) {
    try {
        getClaims(token);  // 파싱 성공 = 유효한 토큰
        return true;
    } catch (JwtException e) {
        return false;       // 서명 불일치, 만료, 형식 오류 등
    }
}
```

---

## 3. JwtAuthenticationFilter — 매 요청마다 토큰 검증

클라이언트가 보낸 JWT 토큰을 검증하고, 인증 정보를 SecurityContext에 설정하는 필터입니다.

```java

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    // 생성자 주입

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 요청 헤더에서 토큰 추출
        String token = resolveToken(request);

        // 2. 토큰이 있고 유효하면 인증 처리
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsername(token);
            String role = jwtTokenProvider.getRole(token);

            // 3. Authentication 객체 생성
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username,                                    // principal
                            null,                                        // credentials (토큰 인증이므로 null)
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))  // 권한
                    );

            // 4. SecurityContext에 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 5. 다음 필터로 진행
        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 "Bearer " 접두사를 제거하고 토큰만 추출
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

### OncePerRequestFilter

`OncePerRequestFilter`를 상속하면 **요청 하나당 정확히 한 번만** 필터가 실행됩니다. 일반 Filter를 상속하면 내부 포워딩 등으로 인해 같은 요청에서 여러 번 실행될 수 있습니다.

### Bearer 토큰

HTTP 표준에서 토큰 기반 인증의 헤더 형식은 다음과 같습니다:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWI...
```

`Bearer`는 "이 토큰의 소유자(bearer)에게 접근을 허용하라"는 의미의 인증 스킴입니다.

### 필터의 동작 흐름

```
[토큰이 있고 유효한 경우]
요청 → 토큰 추출 → 검증 성공 → Authentication 생성 → SecurityContext 저장 → Controller 도달

[토큰이 없는 경우]
요청 → 토큰 없음 → SecurityContext 비어있음 → 다음 필터로 진행
→ 인가 필터에서 "인증 안 됐네" → permitAll이면 통과, authenticated이면 401 반환

[토큰이 유효하지 않은 경우]
요청 → 토큰 추출 → 검증 실패 → SecurityContext 비어있음 → (위와 동일)
```

---

## 4. SecurityConfig에 JWT 필터 등록

```java

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 생성자 주입

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/posts/**").permitAll()
                        .anyRequest().authenticated()
                )
                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

`addFilterBefore`는 기존 필터 체인의 특정 위치 앞에 우리의 커스텀 필터를 삽입합니다. Spring Security의 기본 인증 필터(
`UsernamePasswordAuthenticationFilter`)보다 먼저 JWT 검증을 수행하도록 합니다.

---

## 5. 핵심 정리

| 구성 요소                       | 역할                                             |
|-----------------------------|------------------------------------------------|
| **JwtTokenProvider**        | 토큰 생성, 검증, 정보 추출. 비밀키 관리                       |
| **JwtAuthenticationFilter** | 매 요청마다 토큰을 검증하고 SecurityContext에 인증 정보 설정      |
| **SecurityConfig**          | URL별 접근 규칙 정의, JWT 필터 등록, PasswordEncoder 빈 등록 |
| **application.properties**  | 비밀키, 만료 시간 등 보안 설정값 외부화                        |

---

## 다음 문서

다음 문서에서는 **회원(Member) Entity, 회원가입/로그인 API**, 그리고 **게시글에 작성자 정보를 연결하는 방법**을 설명합니다.

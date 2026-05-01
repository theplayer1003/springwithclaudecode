# SecurityConfig 설정 구조

## Q. HttpSecurity 설정은 어떤 구조로 작성해야 하나?

**A:** 하나의 체인으로, 각 설정을 **한 번만** 호출한다.

```java
http
    .csrf(...)                    // 1. CSRF 설정
    .headers(...)                 // 2. 헤더 설정
    .sessionManagement(...)       // 3. 세션 설정
    .authorizeHttpRequests(...)   // 4. 인가 규칙 (한 번에 모아서)
    .addFilterBefore(...);        // 5. 필터 등록
```

---

## Q. authorizeHttpRequests를 두 번 호출하면 어떻게 되나?

**A:** 두 번째 호출이 첫 번째를 덮어쓸 수 있다. 어떤 규칙이 적용되는지 예측하기 어려워진다.

```java
// 잘못된 예: 두 번 호출
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/**").permitAll()
    .anyRequest().authenticated()
)
.authorizeHttpRequests(auth -> auth         // 첫 번째를 덮어씀
    .requestMatchers("/h2-console/**").permitAll()
)
```

```java
// 올바른 예: 한 번에 모아서
.authorizeHttpRequests(auth ->auth
        .

requestMatchers("/h2-console/**").

permitAll()
    .

requestMatchers("/auth/**").

permitAll()
    .

requestMatchers(HttpMethod.GET, "/posts","/posts/**").

permitAll()
    .

anyRequest().

authenticated()
)
```

---

## Q. authorizeHttpRequests 안에서 규칙 순서가 중요한가?

**A:** 중요하다. Spring Security는 **위에서부터 순서대로 매칭**한다. 먼저 매칭되는 규칙이 적용된다.

```java
.authorizeHttpRequests(auth ->auth
        .

requestMatchers("/h2-console/**").

permitAll()              // 1. 먼저 체크
    .

requestMatchers("/auth/**").

permitAll()                    // 2. 그 다음
    .

requestMatchers(HttpMethod.GET, "/posts","/posts/**").

permitAll()  // 3. 그 다음
    .

anyRequest().

authenticated()                              // 4. 위에서 안 걸리면 여기
)
```

**`anyRequest().authenticated()`는 반드시 마지막이어야 한다.** 이것이 먼저 오면 모든 요청이 인증 필요로 걸리고, 아래의 permitAll() 규칙에 도달하지 못한다.

---

## Q. H2 콘솔 접근 시 추가로 필요한 설정은?

**A:** 두 가지가 필요하다.

```java
// 1. 경로 허용
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/h2-console/**").permitAll()
    // ...
)
// 2. iframe 허용 (H2 콘솔이 iframe을 사용하기 때문)
.headers(headers -> headers
    .frameOptions(frame -> frame.disable())
)
```

이 설정은 **개발 환경에서만 필요**하다. 운영 환경에서는 H2 콘솔 자체를 비활성화하므로 필요 없다.

---

## Q. Spring Boot 기동 실패 시 에러 로그를 어떻게 읽나?

**A:** 방대한 로그에서 핵심을 빠르게 찾는 방법:

### 1. 위에서부터 읽지 않는다

에러 로그의 핵심은 **아래쪽**에 있다.

### 2. `Caused by:`를 검색한다

여러 개가 나오면 **가장 마지막 `Caused by:`가 진짜 원인**이다.

```
에러 A 발생
  Caused by: 에러 B 때문에
    Caused by: 에러 C 때문에      ← 진짜 원인
```

### 3. 핵심 정보 추출

마지막 `Caused by:` 줄에서 두 가지를 본다:

- **예외 클래스 이름** — 어떤 종류의 에러인지
- **메시지** — 구체적으로 뭐가 잘못되었는지

### 4. 우리 코드 찾기

스택 트레이스에서 `com.study.board`가 포함된 줄을 찾으면 우리가 작성한 코드 중 어디서 문제가 발생했는지 알 수 있다.

---

## Q. CommandLineRunner란?

**A:** Spring Boot가 제공하는 인터페이스로, 애플리케이션이 완전히 기동된 직후 딱 한 번 실행되는 코드를 정의한다.

```java
@Component  // Spring 빈으로 등록되어야 실행됨
public class AdminInitializer implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        // 기동 직후 실행할 코드
    }
}
```

주의사항:

- **`@Component` 필수** — 없으면 Spring이 빈으로 인식하지 못해 run()이 호출되지 않음
- 초기 데이터 생성 시 **중복 체크** 필요 — 매 기동마다 실행되므로 이미 존재하는지 확인해야 함

---

## Q. 하드코딩된 비밀번호는 어떻게 분리하나?

**A:** `@Value`를 사용해 `application.properties`에서 읽어온다.

```properties
# application.properties
app.admin.password=test1234
```

```java
@Value("${app.admin.password}")
private String adminPassword;
```

운영 환경에서는 한 단계 더 나아가 환경변수로 대체한다:

```properties
app.admin.password=${ADMIN_PASSWORD}
```

이렇게 하면 설정 파일에도 비밀번호가 남지 않고, 서버 배포 시 환경변수로 주입한다.

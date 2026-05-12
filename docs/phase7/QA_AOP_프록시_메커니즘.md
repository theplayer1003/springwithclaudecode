# AOP와 프록시 메커니즘

`@Transactional`, `@Cacheable`, `@PreAuthorize`, `@Async` — 이 어노테이션들은 모두 **같은 메커니즘** 위에서 동작한다. 그 메커니즘이 AOP(Aspect-Oriented Programming, 관점 지향 프로그래밍)다.

## 1. AOP가 해결하려는 문제

비즈니스 로직(`createComment()`, `findPost()`)에는 본질적인 일 외에도 **공통으로 끼워 넣고 싶은 부가 기능**들이 있다.

- 메서드 시작 전 트랜잭션 시작, 끝나면 commit/rollback
- 메서드 결과를 캐시에 저장하거나, 캐시에서 먼저 찾기
- 호출 전에 권한 확인
- 메서드를 별도 스레드에서 실행

이걸 비즈니스 로직 안에 직접 쓰면 어떻게 될까.

```java
public CommentResponse createComment(...) {
    // 1. 트랜잭션 시작
    TransactionStatus tx = txManager.getTransaction(...);
    try {
        // 2. 권한 확인
        if (!hasPermission()) throw new AccessDeniedException();

        // 3. 실제 비즈니스 로직 (사실 이게 다임)
        Comment saved = commentRepository.save(comment);

        // 4. 트랜잭션 commit
        txManager.commit(tx);
        return CommentResponse.from(saved);
    } catch (Exception e) {
        // 5. 트랜잭션 rollback
        txManager.rollback(tx);
        throw e;
    }
}
```

문제:
- 비즈니스 로직(3번 한 줄)이 부가 코드에 묻혀 안 보인다
- 트랜잭션 처리 방식이 바뀌면 **모든 서비스 메서드를 수정**해야 한다
- 메서드마다 같은 코드를 반복하게 된다

→ 해결: **부가 기능을 별도로 분리하고, 비즈니스 메서드 호출을 가로채서 자동으로 끼워 넣는다.** 이것이 AOP의 발상이다.

## 2. Spring AOP — 프록시 기반 구현

Spring이 AOP를 구현하는 방식은 **프록시(Proxy)** 다.

### 프록시란

**진짜 객체를 바깥에서 감싸는 별개의 객체.** 진짜 객체와 같은 인터페이스/타입을 가지고 있어서, 호출자는 프록시와 진짜 객체를 구별하지 못한다.

```
[ 외부 호출자 ]
       │
       │ @Autowired 로 주입받는 건 진짜 객체가 아니라 "프록시"
       ↓
[ 프록시 객체 ]   ← 실제로 Spring 컨테이너의 빈으로 등록되어 있음
       │  (내부에 진짜 객체에 대한 참조 보유)
       │  메서드 호출을 가로채서 부가 기능 수행
       ↓
[ 진짜 객체 (실제 비즈니스 로직 보유) ]
```

### Spring이 시작 시점에 하는 일

```
1. 빈을 만들면서 클래스 검사
   "이 클래스에 @Async / @Transactional / @Cacheable / @PreAuthorize 가 붙은 메서드가 있나?"

2. 있으면 → 진짜 객체 대신 프록시 객체를 빈으로 등록
   프록시 내부에는 진짜 객체에 대한 참조가 들어 있음

3. 의존성 주입 시점
   @Autowired 로 주입되는 것은 사실 프록시
```

### 호출 시점에 일어나는 일

```
1. 호출자가 method() 호출 → 프록시의 method() 가 실행
2. 프록시가 부가 기능 수행 (트랜잭션 시작, 캐시 확인, 권한 확인 등)
3. 진짜 객체의 method() 호출
4. 진짜 객체 실행이 끝나면 프록시가 마무리 부가 기능 (commit, 캐시 저장 등)
5. 호출자에게 결과 반환
```

## 3. 각 어노테이션의 프록시 동작

같은 메커니즘 위에서, **어떤 부가 기능을 끼워 넣는지만** 다르다.

### @Transactional

```
[프록시 진입]
  → 트랜잭션 begin
  → 진짜 메서드 실행
      ├─ 정상 종료 → commit
      └─ 예외 발생 → rollback
  → 결과 반환
```

### @Cacheable

```
[프록시 진입]
  → 캐시 조회
      ├─ 캐시에 있음 → 진짜 메서드 호출하지 않고 즉시 캐시 값 반환
      └─ 캐시에 없음 → 진짜 메서드 호출 → 결과를 캐시에 저장 → 반환
```

### @PreAuthorize

```
[프록시 진입]
  → SecurityContext 권한 확인
      ├─ 통과 → 진짜 메서드 실행 → 결과 반환
      └─ 실패 → AccessDeniedException
```

### @Async

```
[프록시 진입]
  → TaskExecutor 에 작업 제출 (진짜 메서드를 직접 호출하지 않음!)
  → 호출자에게 즉시 반환
      [별도 스레드에서 진짜 메서드 본문이 비동기로 실행됨]
```

`@Async`만 좀 특별하다. 다른 셋은 "본문은 어쨌든 실행하되 앞뒤로 뭘 한다"인데, `@Async`는 본문 실행을 **다른 스레드에게 시키고 호출자는 즉시 떠난다.**

## 4. 함정 — 자기 호출(self-invocation) 문제

같은 클래스 내부에서 `this.method()` 형태로 어노테이션이 붙은 메서드를 호출하면 **어노테이션의 부가 기능이 무시된다.**

```java
@Service
public class NotificationService {

    public void doSomething() {
        this.sendEmail();   // ❌ @Async 무시됨, 동기 실행
        sendEmail();        // ❌ 위와 동일 (this 생략 형태)
    }

    @Async
    public void sendEmail() {
        // 동기로 실행되어 버림
    }
}
```

### 왜 그런가

프록시는 진짜 객체를 **바깥에서 감싸는** 별개 객체다. **진짜 객체는 자기를 감싼 프록시의 존재를 모른다.**

- 외부 호출자 → 프록시 주입받음 → 프록시.method() → 부가 기능 → 진짜.method() ✅
- 내부 `this.method()` → `this`는 진짜 객체 자신 → 진짜.method() 바로 호출 → 프록시 우회 ❌

`this`가 가리키는 건 자기 자신, 즉 **진짜 객체**다. 프록시는 외곽 껍데기이므로 진짜 객체에서는 닿을 수 없다.

### 헷갈리기 쉬운 점

"스프링이 그 메서드를 프록시로 감싸지 못한 것"이 아니다. **빈은 이미 프록시로 감싸져 있다.** 다만 `this.method()`라는 호출 경로가 프록시를 거치지 않고 진짜 객체로 직진할 뿐이다. **같은 메서드라도 호출 경로에 따라 프록시 적용 여부가 달라진다.**

### 해결책

**(1) 다른 클래스로 분리 — 권장**

`@Async` / `@Transactional` 메서드를 별도 빈으로 빼고, 그 빈을 주입받아 호출한다. 외부 호출 경로가 되므로 프록시를 거친다.

```java
@Service
public class CommentService {
    private final NotificationService notificationService;

    public void createComment(...) {
        notificationService.sendEmail(...);  // ✅ 외부 빈 호출 → 프록시 경유
    }
}

@Service
public class NotificationService {
    @Async
    public void sendEmail(...) { ... }
}
```

대부분의 경우 이렇게 한다. 책임 분리 측면에서도 자연스럽다.

**(2) 자기 자신을 주입받기 — 잘 안 씀**

가능하긴 하지만 가독성이 떨어져 드물게 쓴다.

```java
@Service
public class NotificationService {
    @Autowired
    private NotificationService self;  // 프록시가 주입됨

    public void doSomething() {
        self.sendEmail();  // ✅ 프록시 경유
    }

    @Async
    public void sendEmail() { ... }
}
```

## 5. private/final 메서드에 적용되지 않는 이유

Spring 기본 프록시 방식의 동작 원리 때문이다.

- **`private` 메서드** — 외부에서 호출 자체가 불가능하므로 프록시가 가로챌 수 없다. → `@Async`/`@Transactional` 무시됨
- **`final` 메서드** — CGLIB은 자식 클래스를 동적으로 만들어 부모 메서드를 오버라이드하는 방식으로 가로채는데, `final`은 오버라이드 자체가 불가

→ AOP 어노테이션이 붙은 메서드는 **`public` (또는 적어도 비-final)** 으로 작성한다.

## 6. 정리

- AOP는 비즈니스 로직과 공통 부가 기능을 분리하는 패턴
- Spring AOP는 **프록시**로 구현됨 — 진짜 객체를 감싸는 별도 객체가 빈으로 등록되어 메서드 호출을 가로챔
- `@Transactional`, `@Cacheable`, `@PreAuthorize`, `@Async` 모두 동일 메커니즘. 끼워 넣는 부가 기능만 다름
- **자기 호출은 프록시를 우회**하므로 어노테이션이 무시된다 → 별도 빈으로 분리하는 것이 정석
- private / final 메서드에는 적용되지 않는다 → `public`으로 작성

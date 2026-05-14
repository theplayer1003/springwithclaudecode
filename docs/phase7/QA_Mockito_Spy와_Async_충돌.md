# Mockito Spy와 @Async 충돌

`@SpyBean` / `@MockitoSpyBean` 으로 spy를 만든 빈에 `@Async` 메서드가 있을 때 — 기대한 대로 stubbing이 동작하지 않는 흔한 함정. 본 문서는 실제 학습 중 부딪힌 사건을 통해 메커니즘과 해결책을 정리한다.

## 1. 사건 개요

### 시나리오

비동기 알림 기능을 통합 테스트로 검증하려 했다.

- `CommentNotificationService` (클래스, 인터페이스 없음)에 `@Async` 메서드 `notifyNewComment` 존재
- 통합 테스트에서 `@MockitoSpyBean`으로 spy 등록
- `doAnswer`로 비동기 호출 시점을 가로채고, `CountDownLatch`로 동기화

```java
@SpringBootTest
class CommentAsyncIntegrationTest {
    @MockitoSpyBean
    private CommentNotificationService commentNotificationService;
    
    @Test
    void test() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            invocation.callRealMethod();
            latch.countDown();
            return null;
        }).when(commentNotificationService).notifyNewComment(any(), any(), any());
        
        commentService.createComment(...);
        
        assertThat(latch.await(3, SECONDS)).isTrue();
    }
}
```

### 증상

- `latch.await(3, SECONDS)` 가 `false` 반환 → 3초 동안 latch가 풀리지 않음
- 추가 진단 로그에서 — doAnswer 블록이 **실행되지 않음**
- mockingDetails 결과: `isSpy() == true`, `isMock() == true`
- spy로 등록은 됐는데 stubbing이 호출 가로채기에 적용되지 않은 상태
- Mockito가 `InvalidUseOfMatchersException`을 (afterTestMethod 단계에서) 보고함

## 2. 사전 지식 정리

### 2.1. Mockito mock vs spy

| | mock | spy |
|---|---|---|
| 만드는 방식 | `mock(Class)` / `@Mock` / `@MockitoBean` | `spy(realInstance)` / `@Spy` / `@MockitoSpyBean` |
| 기본 메서드 동작 | 아무것도 안 함 (void) / null/0/false 반환 | **진짜 메서드 실행** |
| 호출 기록 | ✅ verify 가능 | ✅ verify 가능 |
| stubbing | 보통 `when().thenReturn()` | **`doXxx().when()` 권장** |
| 용도 | 의존성 격리 (진짜 동작 안 시킴) | 진짜 동작 + 일부만 덮어쓰기 |

### 2.2. spy에는 왜 `doXxx().when()` 패턴이 권장되나

mockito의 stubbing 형태 두 가지:

```java
// 형태 A: when().thenReturn()
when(mock.method(arg)).thenReturn("hello");

// 형태 B: doXxx().when()
doReturn("hello").when(mock).method(arg);
```

**mock의 경우** — 두 형태 모두 OK.

**spy의 경우** — 형태 A는 위험. 왜냐면 `mock.method(arg)` 호출 시 **진짜 메서드가 실제로 실행**되기 때문이다. 만약 진짜 메서드가 부작용(DB 변경, 파일 쓰기 등)을 일으키면 stubbing 등록 과정에서 의도치 않은 동작 발생.

→ spy에는 **`doXxx().when(spy).method(arg)`** 형태를 쓴다. mockito가 이 형태에서는 진짜 메서드를 실행하지 않고 stubbing을 등록.

학습자 코드는 `doAnswer().when(spy)...` 패턴 — 이 부분은 올바른 사용.

### 2.3. doAnswer 동작

```java
doAnswer(invocation -> {
    // 이 람다가 메서드 호출을 "대체" 실행
    return null;
}).when(spy).method(args);
```

- `method` 호출 시점에 mockito가 가로채서 람다를 실행
- 진짜 메서드가 실행되는 게 아니라, **람다가 그 자리를 차지**
- 람다 안에서 `invocation.callRealMethod()`를 호출하면 진짜 메서드도 추가로 호출 가능

### 2.4. CountDownLatch

자바 표준 동시성 도구. **카운터 + 대기 메커니즘.**

```java
CountDownLatch latch = new CountDownLatch(1);  // 카운터를 1로 시작

// 어느 스레드든
latch.countDown();   // 카운터를 1 줄임 (1 → 0)

// 어느 스레드든
latch.await(3, SECONDS);   // 카운터가 0이 될 때까지 대기 (최대 3초)
// → 0 되면 즉시 통과, 3초 지나면 false 반환
```

**sleep과는 무관.** 그저 동기화 도구. 비동기 테스트에서 *"비동기 작업이 끝났음을 신호로 받기"* 용도로 자주 쓰인다.

자세한 내용은 [CountDownLatch](QA_CountDownLatch.md) 참조.

## 3. 충돌의 본질

### 3.1. 빈 구조 — 두 가지 시나리오

Spring 빈 생명주기에서 두 가지 후처리가 일어난다.

1. **`@Async` 프록시 씌우기** — `AsyncAnnotationBeanPostProcessor` 가 처리. 결과: CGLIB 자식 클래스로 원본을 감쌈.
2. **`@MockitoSpyBean` 처리** — Spring Test의 bean override 메커니즘. 결과: 컨테이너의 빈을 Mockito spy로 교체.

두 처리의 **순서**에 따라 빈 구조가 갈린다.

| 시나리오 | 처리 순서 | 결과 구조 |
|---|---|---|
| **A** | @Async 먼저, 그 위를 spy가 덮음 | `[spy [@Async [원본]]]` |
| **B** | spy 먼저, 그 위를 @Async가 덮음 | `[@Async [spy [원본]]]` |

학습자 환경 출력에서 최외곽 클래스명이 `$$SpringCGLIB$$0` 이었으므로 **시나리오 B에 가까운 것으로 추정**된다.

### 3.2. 어느 시나리오든 의도대로 동작 안 함

**시나리오 A — `[spy [@Async [원본]]]`** (spy가 바깥)

```
외부 호출 → spy.notifyNewComment()
   └─ mockito가 stubbing 확인 (doAnswer 발동 시도)
       └─ 람다 실행 (호출자 스레드에서)
            ├─ threadName ← 호출자 스레드 이름 ❌ (의도와 다름)
            ├─ callRealMethod() → @Async 프록시.method()
            │       └─ 별도 스레드로 작업 위임 → 즉시 리턴
            └─ latch.countDown() (호출자 스레드에서)
```

- doAnswer는 발동하지만 호출자 스레드에서 실행
- 캡처된 스레드 이름이 의도와 어긋남
- callRealMethod는 @Async 프록시로 위임 → 별도 스레드 동작은 하지만 그 안의 sleep 완료를 기다리지 않음

**시나리오 B — `[@Async [spy [원본]]]`** (@Async가 바깥)

```
외부 호출 → @Async 프록시.notifyNewComment()
   └─ 별도 스레드 notify-N로 작업 위임 → 즉시 리턴
         ↓ (notify-N에서)
         spy.notifyNewComment()
            └─ mockito가 stubbing 확인
                └─ ★ 여기서 호출 가로채기 실패 (메커니즘 충돌)
                   doAnswer 발동 안 함, 진짜 메서드만 실행
```

학습자가 부딪힌 시나리오. 의도는 옳았는데 — **CGLIB 프록시가 안쪽 spy의 메서드를 호출할 때, mockito 인터셉터가 끼어들지 못함**.

### 3.3. 왜 mockito 인터셉터가 끼어들지 못하나

CGLIB이 만드는 자식 클래스의 모양 (대략):
```java
class CommentNotificationService$$SpringCGLIB$$0 extends CommentNotificationService {
    private MethodInterceptor interceptor;
    
    @Override
    public void notifyNewComment(...) {
        // @Async 부가 기능 (TaskExecutor에 작업 제출 등)
        interceptor.intercept(this, method, args, methodProxy);
        // methodProxy.invokeSuper(...) 로 부모(원본 또는 spy)의 메서드를 직접 호출
    }
}
```

핵심은 **`methodProxy.invokeSuper(...)`**. 이 호출은 `super.method()` 와 비슷한 동작 — **부모 클래스의 메서드를 vtable을 우회해 직접 호출**한다.

Mockito의 메서드 가로채기는 **동적 디스패치 (this.method() 호출 시 vtable lookup)** 에 의존하는데, `super.method()` 같은 정적 디스패치 호출에는 끼어들 자리가 없다.

또한 라이브러리 두 개가 각자 자체적으로 자식 클래스를 만들려고 하는데, **자바는 단일 상속**이라 클래스 하나에 두 부모를 동시에 가질 수 없다. 한쪽이 다른 쪽 위에 한 번 더 감싸야 하는데, 그 배치가 자연스럽게 합쳐지지 않는다.

### 3.4. 충돌의 한 줄 요약

> **"두 라이브러리가 모두 동적 자식 클래스를 만드는 방식으로 메서드 가로채기를 구현한다. 두 가로채기가 한 객체에 겹쳐지면 호출 경로가 어긋나, mockito 가로채기가 무력화된다."**

## 4. 해결책 — 시도와 한계

### 4.1. 1차 시도 — Interface 기반 분리

대상 클래스를 **인터페이스로 분리**한다.

```java
// 인터페이스
public interface CommentNotificationService {
    void notifyNewComment(String email, String username, String content);
}

// 구현체
@Service
public class CommentNotificationServiceImpl implements CommentNotificationService {
    @Async("notificationExecutor")
    @Override
    public void notifyNewComment(...) { ... }
}
```

**기대 효과**: 인터페이스가 있으면 Spring이 **JDK Dynamic Proxy를 사용**할 수 있다 (`spring.aop.proxy-target-class=false` 설정 필요). JDK Proxy는 자바 표준 메커니즘이라 Mockito와 같은 표준 위에서 동작하므로 호환성이 좋아진다는 이론적 기대.

### 4.2. 실제 결과 — 부분적 해결, 완전 해결은 아님

학습 중 실제 검증한 결과:

- ✅ **JDK Proxy로 전환은 성공** — 클래스명이 `$$SpringCGLIB$$0` 에서 `jdk.proxy3.$Proxy184` 로 바뀜
- ✅ **`@Async` 자체는 정상 동작** — `notify-N` 스레드에서 진짜 메서드 본문이 실행됨
- ❌ **그러나 `doAnswer` 발동은 여전히 실패** — mockito가 spy로 인식은 하지만 호출 가로채기에 적용되지 않음

즉 **interface 분리만으로는 충돌이 완전히 해결되지 않는다.** JDK Proxy로 만들어진 빈에 mockito spy를 또 한 번 감싸는 구조에서도, Spring AOP의 호출 경로가 mockito 인터셉터를 우회하는 또 다른 미묘한 어긋남이 남는다. 정확한 메커니즘은 Spring Test의 빈 처리 순서, mockito의 ByteBuddy 인터셉터 등록 방식 등 라이브러리 내부 구현에 달려 있으며, 환경(Spring Boot 버전 / Java 버전 / mockito 버전)마다 결과가 다를 수 있다.

### 4.3. 부가 효과 — 코드 품질 향상은 여전히 유효

인터페이스 분리는 테스트 충돌 해결에는 부분적이지만, **설계 품질 측면에서는 명확한 이득**이 있다.

- **DIP (의존성 역전 원칙)** 적용 — 호출자는 구현이 아닌 추상에 의존
- 알림 채널 추가 시 자연스러운 확장 — `EmailNotificationServiceImpl`, `PushNotificationServiceImpl` 등
- 테스트 더블 작성이 쉬워짐 (실무에서 가짜 구현체로 대체 가능)

→ 테스트 호환성 목적이 아니더라도, 외부 시스템과 통신하는 서비스는 **인터페이스 기반으로 설계하는 것이 일반적으로 좋은 선택**이다.

자세한 자바/Spring 프록시 메커니즘 차이는 [JDK Dynamic Proxy vs CGLIB](QA_프록시_JDK_vs_CGLIB.md) 참조.

### 4.4. 실제로 통한 우회법 — Executor를 `@TestBean`으로 교체

서비스 자체를 spy로 감싸려는 시도(라이브러리 충돌 영역)를 포기하고, **서비스가 사용하는 인프라(Executor)를 교체**해서 비동기 동작을 가로채는 접근이 실효적으로 동작했다.

**핵심 아이디어 — 검증 추상화 수준의 전환**:

> **"서비스 메서드 호출을 가로채는 대신, 그 서비스가 사용하는 스레드 풀(Executor) 자체를 학습자가 만든 객체로 교체한다. 그러면 mockito와 Spring AOP의 충돌 영역(서비스 빈) 자체를 건드리지 않게 된다."**

```java
@SpringBootTest
public class CommentAsyncIntegrationTest {
    
    private static final CountDownLatch latch = new CountDownLatch(1);
    private static final AtomicReference<String> capturedThreadName = new AtomicReference<>();
    
    // @TestBean — mockito가 아닌 학습자 정의 객체로 빈을 교체
    @TestBean(name = "notificationExecutor", methodName = "createTestExecutor")
    private ThreadPoolTaskExecutor notificationExecutor;
    
    // 반드시 static 메서드. Spring이 인스턴스 없이 호출
    static ThreadPoolTaskExecutor createTestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setThreadNamePrefix("test-async-pool-");
        
        // TaskDecorator — Executor가 작업을 실행하기 직전에 가로채는 후크
        executor.setTaskDecorator(runnable -> () -> {
            try {
                capturedThreadName.set(Thread.currentThread().getName());
                runnable.run();   // 진짜 작업 실행 (sleep 1.5초 등)
            } finally {
                latch.countDown();   // 비동기 완료 신호
            }
        });
        executor.initialize();
        return executor;
    }
    
    @Test
    void test() throws InterruptedException {
        commentService.createComment(...);
        
        assertThat(latch.await(3, SECONDS)).isTrue();   // 비동기 완료까지 대기
        assertThat(capturedThreadName.get()).contains("test-async-pool-");
    }
}
```

**왜 이게 동작하는가**:

- `Executor`는 `@Async` 어노테이션의 대상이 아니므로 프록시로 감싸지지 않는다 — 평범한 빈
- mockito가 그 빈을 직접 교체해도 **프록시 충돌이 일어날 자리가 없다**
- `TaskDecorator`는 Executor가 받는 모든 작업(Runnable)을 다른 Runnable로 감싸는 후크. 비동기 작업의 시작/종료 시점을 자유롭게 가로챌 수 있다
- 그 안에서 `CountDownLatch`로 메인 테스트 스레드와 동기화

**검증되는 항목**:

| 우리가 원했던 검증 | 이 방식으로 어떻게 충족되는가 |
|---|---|
| 비동기 인프라가 동작한다 | latch가 3초 안에 풀림 |
| 별도 스레드에서 실행된다 | capturedThreadName이 `test-async-pool-` |
| `@Async` 메커니즘이 작동한다 | Executor가 호출됐다는 사실 자체가 증거 |
| 알림 메서드 본문이 실행된다 | TaskDecorator의 `runnable.run()` 안에서 진짜 메서드 실행 |

명시적 `verify(notifier).notifyNewComment(...)` 는 없지만, **"Executor가 작업을 받아 별도 스레드에서 실행했다 = 알림 메서드가 호출되어 비동기로 동작했다"** 는 간접 증명이 모든 검증 항목을 충족한다.

### 4.5. 사용된 새 개념 정리

**`@TestBean`** — Spring Test의 빈 override 방식 세 번째 옵션

| 어노테이션 | 동작 |
|---|---|
| `@MockitoBean` | 빈을 mockito mock으로 교체 |
| `@MockitoSpyBean` | 빈을 mockito spy로 교체 (진짜 객체 감쌈) |
| `@TestBean` | 빈을 **학습자가 직접 만든 객체로 교체** (mockito 무관) |

`@TestBean`은 정적 팩토리 메서드 이름을 지정한다 (`methodName="..."`). 그 메서드가 반환하는 객체가 새 빈이 된다. mockito가 끼어들지 않으므로 프록시 충돌 자체가 발생할 수 없다.

**`TaskDecorator`** — `ThreadPoolTaskExecutor`의 작업 후킹

`Executor.execute(Runnable)`가 호출될 때, Runnable을 다른 Runnable로 감싸서 풀에 던지게 하는 후크. 비동기 작업의 시작/종료 시점에 임의 로직을 끼워넣을 수 있다 (로깅, 트레이싱, MDC 컨텍스트 전파, 테스트 동기화 등 실무 활용도 높음).

```java
executor.setTaskDecorator(originalRunnable -> {
    return () -> {                              // 새 Runnable 반환
        // 시작 시 로직
        try {
            originalRunnable.run();              // 원래 작업
        } finally {
            // 종료 시 로직
        }
    };
});
```

### 4.6. 정적 필드 주의점

위 코드에서 `latch`와 `capturedThreadName`이 `static`인 이유는, `createTestExecutor()` 가 정적 메서드라 인스턴스 필드를 참조할 수 없기 때문이다.

**잠재 함정**: 같은 클래스에 비동기 테스트 메서드가 여러 개 추가되면 — 정적 필드가 테스트 간에 공유된다. `CountDownLatch`는 한 번 0이 되면 재사용 불가하므로, 두 번째 테스트는 이미 풀린 latch를 받게 되어 의도와 다르게 즉시 통과한다.

→ 비동기 테스트가 여러 개라면 `@BeforeEach`에서 latch와 capturedThreadName을 새로 초기화해야 한다.

## 5. 그 외 대안 (참고)

위 4.4의 `@TestBean` + `TaskDecorator` 방식 외에도 다음 옵션들이 있다.

### (1) `@MockitoBean` + `verify(timeout())`

spy 대신 **mock**으로 빈을 교체. 진짜 메서드는 실행 안 됨.

```java
@MockitoBean
private CommentNotificationService commentNotificationService;

@Test
void test() {
    commentService.createComment(...);
    
    verify(commentNotificationService, timeout(3000))
        .notifyNewComment(any(), any(), any());
}
```

**장점**: 가장 단순. 운영 코드 변경 없음.

**한계**:
- 진짜 메서드가 실행되지 않으므로 sleep도 안 됨
- "별도 스레드에서 실행됐는가" 검증 불가 (스레드 이름 캡처 어려움)
- 사실상 단위 테스트와 검증 영역이 거의 같아짐

### (2) 동기 Executor로 교체

테스트 환경에서 `@Async` Executor를 `SyncTaskExecutor` 로 swap. 비동기가 사실상 동기로 실행됨.

**한계**: "비동기 동작 자체"를 검증하려는 통합 테스트의 목적과 어긋남.

### 비교

| 방식 | 검증 강도 | 운영 코드 영향 | 비고 |
|---|---|---|---|
| `@TestBean` + `TaskDecorator` (4.4) | 강 | 없음 | **본 학습에서 실제로 통한 방식** |
| `@MockitoBean` + `verify(timeout())` | 중 | 없음 | 가장 단순. "비동기로 호출됐다"만 검증 |
| 동기 Executor 교체 | 약 | 없음 | 비동기 검증 목적 자체와 어긋남 |
| `@MockitoSpyBean` (직접 spy) | — | 없음 | **현 시점 환경에서는 동작하지 않음** |

## 6. 학습 노트 — 이 사건의 메타 교훈

이번 학습에서 가장 큰 교훈은 기술적 해결책 자체보다 **사고의 전환** 이었다.

**원래 접근**: *"서비스 메서드 호출을 가로채야 한다 → spy를 어떻게 동작시킬지에 매달림 → 라이브러리 충돌의 깊은 영역으로 들어감"*

**전환된 접근**: *"같은 본질(@Async 동작)을 검증하되, 충돌 영역을 회피할 수 있는 다른 추상화 수준이 없는가? → 서비스가 사용하는 Executor를 가로채면 된다"*

> 실무에서 라이브러리 통합 지점에 깊이 끼인 문제를 만날 때, **그 지점을 직접 풀려 하지 말고 한 단계 다른 추상화 수준에서 우회**하는 사고가 흔히 효과적이다.

## 7. 정리

- `@SpyBean` / `@MockitoSpyBean` + `@Async` 조합은 **CGLIB / JDK Proxy + mockito ByteBuddy 두 동적 프록시 라이브러리의 충돌**로 인해 stubbing이 무력화될 수 있다
- mockingDetails 상으로는 spy로 인식되지만, **실제 호출 가로채기가 실패**하는 미묘한 상태
- **interface 분리만으로는 완전히 해결되지 않을 수 있다** — 환경에 따라 다름. JDK Proxy 채택 자체는 효과적이지만 그 위에 또 spy를 감싸는 구조의 어긋남이 남음
- 실효적 우회법: **`@TestBean`으로 Executor를 교체 + `TaskDecorator`로 비동기 시점 후킹** — mockito와 Spring AOP의 충돌 영역 자체를 회피
- 대안적 우회법: **`@MockitoBean` + `verify(timeout())`** — 가장 단순. 검증 강도는 약함
- 라이브러리 통합 문제는 한 추상화 수준에서 풀기 어려우면 **다른 추상화 수준에서 우회**하는 사고 전환이 종종 효과적이다

# CountDownLatch — 비동기 테스트의 동기화 도구

비동기 테스트에서 *"비동기 작업이 끝나길 기다린 후 검증한다"* 는 일을 어떻게 표현할 것인가. 자바 표준 라이브러리(`java.util.concurrent.CountDownLatch`)가 제공하는 동시성 도구.

## 1. 핵심 정체 — "카운터 + 신호 대기"

`CountDownLatch` 는 **0이 될 때까지 줄어드는 카운터**와 **그 카운터가 0이 될 때까지 기다리는 메커니즘**의 결합이다.

```java
CountDownLatch latch = new CountDownLatch(N);   // 카운터를 N으로 초기화

// 어느 스레드에서든
latch.countDown();         // 카운터 -1

// 어느 스레드에서든
latch.await();             // 카운터가 0이 될 때까지 대기 (무한)
latch.await(3, SECONDS);   // 카운터가 0이 될 때까지 대기 (최대 3초)
```

> ⚠️ 자주 헷갈리는 점: **sleep과 무관하다.** 프록시와도 무관하다. 그저 카운터를 깎고 기다리는 동기화 도구일 뿐.

## 2. 동작 메커니즘

### 2.1. 초기 상태

```java
CountDownLatch latch = new CountDownLatch(1);
```

→ 내부 카운터 = 1. 이 1이 0이 되어야 await가 통과한다.

### 2.2. countDown — 카운터 깎기

```java
latch.countDown();   // 1 → 0
```

- 어느 스레드에서든 호출 가능
- 한 번 호출할 때마다 -1
- 0 이하로는 내려가지 않음 (이미 0인 상태에서 또 호출해도 효과 없음, 예외도 없음)

### 2.3. await — 대기

```java
latch.await();                    // 무한 대기
latch.await(3, TimeUnit.SECONDS); // 최대 3초 대기, boolean 반환
```

- 카운터가 0이 되면 즉시 깨어나서 다음 코드 진행
- 시간 초과 시 `false` 반환
- 정상 통과 시 `true` 반환
- 대기 중 인터럽트 받으면 `InterruptedException` 던짐

### 2.4. 일방향성

CountDownLatch는 **재사용 불가**다. 0이 된 카운터를 다시 늘리는 방법이 없다. 한 번 쓰고 버린다. 반복적으로 동기화가 필요하면 `CyclicBarrier`, `Phaser` 등 다른 도구를 사용.

## 3. 비동기 테스트에서의 활용 패턴

### 3.1. 기본 패턴

```java
@Test
void test() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    
    // 비동기 작업이 끝나면 신호를 보내도록 설정
    doAnswer(invocation -> {
        invocation.callRealMethod();
        latch.countDown();   // ← 비동기 작업 완료 신호
        return null;
    }).when(spy).asyncMethod(any());
    
    // 비동기 트리거
    service.triggerAsync();
    
    // 비동기 완료 대기
    assertThat(latch.await(3, SECONDS)).isTrue();
}
```

흐름:
- 비동기 메서드 호출 → spy의 doAnswer 발동 (별도 스레드에서)
- 람다 마지막에 `latch.countDown()` → 카운터 1 → 0
- 메인 테스트 스레드는 `latch.await()` 에서 대기 중 → 깨어남
- 다음 코드(검증) 진행

### 3.2. 여러 비동기 작업 동기화

여러 비동기 작업이 모두 끝나야 검증할 수 있다면 카운터를 늘림:

```java
CountDownLatch latch = new CountDownLatch(3);   // 작업 3개 기다림

// 각 비동기 작업이 끝날 때마다 countDown
// 모두 끝나면 카운터 0 → await 통과
```

### 3.3. 추가 정보 캡처

비동기 작업 결과를 검증하려면 별도 변수에 캡처:

```java
AtomicReference<String> capturedThreadName = new AtomicReference<>();

doAnswer(invocation -> {
    capturedThreadName.set(Thread.currentThread().getName());  // ← 캡처
    invocation.callRealMethod();
    latch.countDown();
    return null;
}).when(spy).asyncMethod(any());

// ...

latch.await(3, SECONDS);
assertThat(capturedThreadName.get()).startsWith("notify-");
```

`AtomicReference` 는 멀티스레드 환경에서 안전한 변수 캡처를 위해 사용. 일반 변수는 스레드 간 메모리 가시성 문제가 있을 수 있다.

## 4. 주의점

### 4.1. 시간 제한을 두지 않으면 무한 대기

```java
latch.await();   // ← timeout 없음. 카운터가 영영 0이 안 되면 무한 대기
```

테스트에서는 **반드시 timeout 옵션을 사용**한다. countDown이 실수로 호출 안 되는 상황에서 테스트가 영원히 멈추는 걸 막기 위해.

### 4.2. await의 InterruptedException

```java
public void await() throws InterruptedException
```

대기 중 스레드 인터럽트가 발생하면 예외가 던져진다. 테스트 메서드 시그니처에 `throws InterruptedException` 을 명시하거나 try-catch로 처리.

```java
@Test
void test() throws InterruptedException {
    // ...
    latch.await(3, SECONDS);
}
```

### 4.3. countDown이 호출 안 되면 await는 timeout

비동기 작업이 어떤 이유로 실행 안 되면 countDown이 호출되지 않는다 → await는 timeout으로 `false` 반환.

→ `false`가 반환된다는 것은 **"비동기 작업이 시간 안에 완료되지 않았다"** 는 강력한 증거. 즉 비동기 동작이 정상적이지 않다는 신호다.

본 학습에서 부딪힌 `@SpyBean` + `@Async` 충돌 사례에서, 이 timeout false가 진단의 핵심 단서였다.

## 5. 비교 — 비슷한 도구들

자바 동시성 라이브러리에는 비슷한 도구가 여럿 있다.

| 도구 | 용도 |
|---|---|
| `CountDownLatch` | 일회용 카운터. N번 카운트다운 후 0이 되면 대기 해제 |
| `CyclicBarrier` | 재사용 가능 카운터. 정해진 스레드 수가 모두 모이면 다 같이 진행 |
| `Phaser` | 더 유연한 동기화. 동적 참여자 수, 여러 단계 |
| `Semaphore` | 동시 접근 가능 스레드 수 제한 |
| `CompletableFuture` | 결과/예외/체이닝 가능. 비동기 메서드 반환 타입으로도 활용 |

비동기 테스트에서 가장 자주 쓰이는 건 `CountDownLatch` 또는 `CompletableFuture`. 운영 메서드 반환 타입이 `CompletableFuture` 면 굳이 latch가 필요 없을 수 있다 ([비동기 처리와 @Async](01_비동기_처리와_@Async.md) 3.2절 참조).

## 6. 정리

- `CountDownLatch` 는 자바 표준의 일회용 카운터 동기화 도구
- 핵심 연산: `countDown()` 으로 -1, `await(timeout)` 으로 0이 될 때까지 대기
- sleep과 무관. 그저 다른 스레드의 작업 완료를 알리는 신호 메커니즘
- 비동기 테스트에서 *"비동기 작업이 끝나길 기다린 후 검증"* 패턴에 활용
- 반드시 timeout 옵션 사용 (무한 대기 방지)
- timeout이 발생했다는 사실 자체가 비동기 동작에 문제가 있음을 알려주는 진단 단서

# 비동기 처리와 @Async

## 1. 문제 — 동기 처리만으로는 부족한 순간

### 시나리오

게시판에 댓글이 달리면 게시글 작성자에게 **이메일 알림**을 보내야 한다는 요구사항이 추가됐다. 이메일 발송은 외부 SMTP 서버 호출이 포함되어 평균 1.5초가 걸린다.

가장 단순하게 구현하면:

```java
@Service
public class CommentService {

    @Transactional
    public CommentResponse createComment(...) {
        Comment saved = commentRepository.save(comment);    // 1. DB 저장 (~50ms)
        mailService.sendNotification(post.getAuthor(), ...);// 2. 이메일 발송 (~1500ms)
        return CommentResponse.from(saved);                 // 3. 응답 반환
    }
}
```

동작은 한다. 그러나 이 코드에는 **서로 다른 세 가지 문제**가 있다.

### 1.1. 트랜잭션과 외부 시스템의 결합

`@Transactional` 메서드 안에서 외부 시스템을 호출하면 다음과 같은 일이 일어날 수 있다.

```
1. 댓글 DB 저장 (아직 commit 전)
2. 이메일 발송 → 사용자 메일함에 즉시 도착
3. 응답 반환 직전 어떤 예외 발생 → 트랜잭션 rollback
4. 댓글은 DB에서 사라졌지만, 이메일은 이미 도착해버림 (되돌릴 수 없음)
```

DB 트랜잭션은 rollback으로 되돌릴 수 있지만, 외부 시스템에서 발생한 효과(이메일, SMS, 결제 호출 등)는 되돌릴 수 없다. **DB 상태와 외부 시스템 상태 사이에 정합성이 깨진다.**

### 1.2. 응답 지연 — 사용자 경험

이메일 발송이 1.5초 걸린다는 건, 사용자가 "댓글 등록" 버튼을 누르고 **1.5초를 기다려야 응답을 받는다**는 뜻이다. 댓글 자체 처리는 50ms면 끝나는데, 부수 작업 때문에 응답이 30배 느려진다.

사용자는 "이 사이트 왜 이렇게 느려?" 라고 느낀다. 댓글이 달렸다는 사실과 이메일 발송 완료 여부는 사용자 입장에서 별개인데도.

### 1.3. 장애 전파 — 시스템 안정성

SMTP 서버에 일시적 장애가 발생해 평소 1.5초가 30초로 늘어났다고 가정. 댓글 요청은 초당 10개씩 들어온다.

```
시점       동작                                     점유 스레드 수
T=0s       댓글 10개 요청 → 10 스레드 점유                10
T=1s       댓글 10개 더 → 20 스레드 점유                  20
...
T=20s      누적 200 스레드 점유                          200 ← 톰캣 기본 풀 한계
T=20s+α    새 요청 들어옴 → 처리할 스레드 없음            대기/거부
```

스레드 풀이 고갈되면 **댓글과 무관한 다른 API(게시글 조회, 로그인 등)도 모두 응답이 안 된다.** 한 외부 시스템의 장애가 무관한 기능까지 마비시키는 것을 **장애 전파(cascading failure)** 라고 한다.

### 세 가지 문제는 별개다

세 문제는 같이 묶여 생각되기 쉽지만, **각자 다른 해법이 필요하다.**

| 해법                          | 트랜잭션 결합 | 응답 지연 | 장애 전파  |
|-----------------------------|---------|-------|--------|
| 동기 호출 (트랜잭션 안)              | ❌       | ❌     | ❌      |
| 트랜잭션 밖으로 옮긴 동기 호출           | ✅       | ❌     | ❌      |
| **비동기 호출 (별도 스레드)**         | ✅       | ✅     | ✅ (부분) |
| 메시지 큐 + 비동기 컨슈머             | ✅       | ✅     | ✅      |

이메일 호출을 트랜잭션 밖으로 빼는 것만으로는 응답 지연과 장애 전파가 해결되지 않는다. **별도 스레드(또는 별도 프로세스)에서 처리해야** 응답 지연과 장애 전파가 함께 풀린다.

소주제 1에서는 **별도 스레드** 단계인 `@Async`를 다룬다. **별도 프로세스** 단계인 메시지 큐는 소주제 3에서 다룬다.

## 2. 비동기 처리의 본질

비동기의 핵심은 한 문장이다.

> **"요청을 던지고, 호출자는 자기 할 일을 한다."**

호출자 스레드는 결과를 기다리지 않는다. 작업을 다른 스레드에게 위임하고 즉시 다음 코드로 진행한다.

```
[ 동기 ]
호출자 ─[메서드 호출]─[기다림 1.5초]─[리턴 받음]─[다음 코드]──>

[ 비동기 ]
호출자 ─[메서드 호출]─[즉시 리턴]─[다음 코드]──>
                │
                └─→ [다른 스레드] ─[실제 작업 1.5초]─[종료]
```

호출자 입장에서 메서드 호출의 비용이 "본문 실행 시간"이 아니라 "다른 스레드에 작업을 던지는 비용"이 된다. 후자는 무시할 수 있을 만큼 작다.

## 3. Spring의 @Async

Spring은 `@Async` 어노테이션 하나로 메서드를 비동기로 만들어 준다.

```java
@Service
public class NotificationService {

    @Async
    public void sendCommentNotification(Long postId) {
        // 외부 SMTP 호출, 1.5초 소요
    }
}
```

사용하는 쪽:

```java
notificationService.sendCommentNotification(postId);  // 즉시 리턴
log.info("이 로그가 이메일 발송보다 먼저 찍힌다");
```

활성화하려면 어딘가의 `@Configuration` 클래스 또는 메인 클래스에 `@EnableAsync`가 붙어 있어야 한다.

```java
@SpringBootApplication
@EnableAsync
public class BoardApplication { ... }
```

### 3.1. 동작 메커니즘 — 프록시 + TaskExecutor

`@Async`가 동작하는 흐름은 다음과 같다.

```
[애플리케이션 시작 시]
  Spring이 NotificationService 빈을 만들 때 @Async를 감지
  → 진짜 객체 대신 "프록시 객체"를 빈으로 등록

[호출 시]
  외부 호출자가 sendCommentNotification(...) 을 호출
  → 사실은 프록시의 메서드가 먼저 실행됨
  → 프록시는 본문을 직접 실행하지 않고, TaskExecutor에 작업으로 제출
  → 호출자에게 즉시 리턴
  → TaskExecutor 풀의 어떤 스레드가 작업 큐에서 꺼내 진짜 본문 실행
```

프록시 메커니즘 일반(다른 어노테이션과의 비교, 자기 호출 문제 등)은 [AOP/프록시 메커니즘](QA_AOP_프록시_메커니즘.md)에서 자세히 다룬다.

### 3.2. 반환 타입

`@Async` 메서드의 반환 타입은 세 종류 중 하나여야 한다.

**(1) `void` — 결과를 알 필요 없을 때**

```java
@Async
public void sendCommentNotification(...) { ... }
```

가장 흔한 형태. "발사 후 망각(fire-and-forget)" 스타일. 호출자는 결과/예외를 받을 방법이 없다.

> ⚠️ `void` 비동기 메서드에서 예외가 발생하면 호출자는 알지 못한다. 별도로 `AsyncUncaughtExceptionHandler`를 등록해야 잡을 수 있다.

**(2) `CompletableFuture<T>` — 결과나 예외를 받고 싶을 때**

```java
@Async
public CompletableFuture<EmailResult> sendCommentNotification(...) {
    EmailResult result = mailServer.send(...);
    return CompletableFuture.completedFuture(result);
}

// 호출 측
CompletableFuture<EmailResult> future = notificationService.sendCommentNotification(...);
future.thenAccept(result -> log.info("이메일 발송 완료: {}", result));
future.exceptionally(ex -> { log.error("발송 실패", ex); return null; });
```

호출자는 즉시 `CompletableFuture`를 돌려받는다. 이 객체는 "**미래에 결과가 들어올 자리(placeholder)**"다. 작업이 끝나면 그 자리에 결과가 채워지고, 등록해 둔 콜백이 실행된다.

**(3) `Future<T>` — 레거시**

`CompletableFuture`의 옛 버전. 콜백 체이닝이 불가능해 현대 코드에서는 거의 쓰지 않는다.

### 3.3. 스레드 풀 (TaskExecutor) 설정

`@Async`가 작업을 던지는 곳이 `TaskExecutor`다. 별도 설정 없이 `@EnableAsync`만 켜면 Spring이 기본 Executor를 만들어 쓰는데, 이건 **풀이 아니라 매 호출마다 새 스레드를 생성**한다(`SimpleAsyncTaskExecutor`). 운영 환경에서는 반드시 직접 설정해야 한다.

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);          // 평소 유지 스레드 수
        executor.setMaxPoolSize(20);          // 최대 스레드 수
        executor.setQueueCapacity(100);       // 작업 대기 큐 크기
        executor.setThreadNamePrefix("notify-");
        executor.initialize();
        return executor;
    }
}
```

여러 Executor를 만들고 메서드별로 골라 쓸 수 있다.

```java
@Async("notificationExecutor")
public void sendCommentNotification(...) { ... }
```

이렇게 분리하면 알림 작업이 폭주해도 다른 비동기 작업(예: 통계 집계)이 영향받지 않는다.

스레드 풀의 일반 개념(커넥션 풀과의 비교 포함)은 [스레드 풀 vs 커넥션 풀](QA_스레드풀_vs_커넥션풀.md)에서 다룬다.

### 3.4. 작업 큐가 가득 차면 — 거부 정책

`MaxPoolSize`만큼 스레드도 모두 사용 중이고, `QueueCapacity`만큼 큐도 가득 찼을 때 새 작업이 들어오면 — 이걸 어떻게 처리할지가 **거부 정책(RejectedExecutionHandler)** 이다.

| 정책                    | 동작                                  |
|-----------------------|-------------------------------------|
| `AbortPolicy` (기본)    | 예외(`RejectedExecutionException`) 발생 |
| `CallerRunsPolicy`    | 호출자 스레드가 직접 실행 (역압 효과)             |
| `DiscardPolicy`       | 조용히 폐기                              |
| `DiscardOldestPolicy` | 큐의 가장 오래된 작업 폐기 후 새 작업 추가          |

알림처럼 "유실되어도 큰 문제는 없지만 시스템은 살아 있어야 하는" 작업에는 `DiscardPolicy`나 `CallerRunsPolicy`가 자주 쓰인다.

## 4. @Async 사용 시 주의점

### 4.1. 자기 호출 문제

같은 클래스 안에서 `this.method()` 형태로 `@Async` 메서드를 호출하면 — 어노테이션이 **무시되고 동기 실행된다.**

```java
@Service
public class CommentService {
    public void createComment(...) {
        commentRepository.save(...);
        this.sendNotification(...);  // ❌ 프록시 우회, 동기 실행
    }

    @Async
    public void sendNotification(...) { ... }
}
```

→ 해결: `@Async` 메서드는 별도 클래스(별도 빈)에 두고 의존성 주입으로 호출한다. 자세한 원리는 [AOP/프록시 메커니즘](QA_AOP_프록시_메커니즘.md) 참조.

### 4.2. 트랜잭션 경계와 비동기

비동기 메서드는 **호출자의 트랜잭션과 완전히 분리된** 새 스레드에서 실행된다. 호출자 트랜잭션이 commit되기 전에 비동기 메서드가 DB를 조회하면 — 호출자가 방금 저장한 데이터를 못 볼 수 있다.

```java
@Transactional
public void createComment(...) {
    Comment saved = commentRepository.save(comment);   // 아직 commit 전
    notificationService.sendNotification(saved.getId()); // 비동기 → 별도 스레드
    // 비동기 메서드가 saved.getId()로 DB 조회 시 못 찾을 수 있음
}
```

비동기 스레드는 호출자 트랜잭션과 별개라서 아직 commit되지 않은 데이터를 조회할 수 없다(`READ_COMMITTED` 격리 수준 기준).

→ 해결책으로 보통 **`@TransactionalEventListener(AFTER_COMMIT)`** 을 함께 쓴다. 소주제 2(이벤트 발행/구독)에서 다룬다.

### 4.3. private/final 메서드에는 적용되지 않음

Spring의 기본 프록시 방식(JDK 동적 프록시 또는 CGLIB) 한계 때문에:

- `private` 메서드 — 외부에서 호출할 수 없어 가로챌 수 없음
- `final` 메서드 — CGLIB 프록시가 오버라이드할 수 없음

`@Async`는 `public` 메서드에만 적용된다.

### 4.4. 반환 타입은 void / Future / CompletableFuture만

`@Async` 메서드가 일반 객체(예: `String`, `User`)를 반환하면 — 그 반환값은 항상 `null`이 된다. 프록시가 즉시 리턴해 버리므로 본문 실행이 끝나지 않은 시점에 반환되기 때문이다. 컴파일 에러는 안 나지만 동작이 무의미하므로 사실상 금지.

## 5. 정리

- 동기 처리는 (1) 트랜잭션-외부시스템 결합, (2) 응답 지연, (3) 장애 전파라는 **별개의 세 문제**를 만든다.
- 비동기 처리는 (2)와 (3)을 함께 푼다. 트랜잭션 결합 문제는 이벤트 패턴이나 메시지 큐에서 추가로 다룬다.
- Spring은 `@Async`로 비동기 처리를 제공한다. 동작은 **프록시 + TaskExecutor** 메커니즘 위에서 일어난다.
- 반환 타입은 `void` 또는 `CompletableFuture<T>`. 결과를 추적할 필요가 있으면 `CompletableFuture`.
- 운영 환경에서는 **반드시 `ThreadPoolTaskExecutor`를 직접 설정**한다. 기본 Executor는 풀이 아니다.
- 함정: 자기 호출 무시, 트랜잭션 경계 차이, public 메서드 한정, 반환 타입 제한.

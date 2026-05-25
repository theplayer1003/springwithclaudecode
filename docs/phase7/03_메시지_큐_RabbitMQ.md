# 메시지 큐와 RabbitMQ

## 1. 문제 — 같은 JVM 안에서의 한계

소주제 2까지 만든 흐름은 다음과 같았다.

```
[하나의 자바 애플리케이션 프로세스]
  CommentService
   → publishEvent(CommentCreatedEvent)
   → CommentNotificationListener (@TransactionalEventListener)
   → CommentNotificationService.notifyNewComment (@Async)
   → 별도 스레드에서 메일 발송
```

이벤트 패턴으로 *결합* 을 끊었고 `@Async` 로 *시간 분리* 를 했지만 — 여전히 **모든 것이 한 JVM 프로세스 안에서 동작**한다. 이게 만들어내는 한계:

| 한계 | 결과 |
|---|---|
| 한 컴포넌트 장애가 다른 컴포넌트에 영향 | 메일 발송 코드의 OOM → 게시판 전체 다운 |
| 알림 트래픽 폭주 시 본 시스템 영향 | 알림 처리 부하가 본 시스템 자원과 경쟁 |
| 메시지 유실 | 애플리케이션 재시작 시 처리 중 알림 사라짐 |
| 독립 배포/스케일링 불가 | 알림 코드 수정해도 본 시스템 재배포 |
| 다른 언어 시스템과 통신 불가 | JVM 한정 |

→ 알림 시스템을 **별도 프로세스로 분리** 하고 싶다는 요구. 이것이 메시지 큐의 동기.

## 2. 분리의 세 가지 층위

지금까지 학습한 도구들의 *분리* 가 무엇을 분리하는지 정리:

| 도구 | 분리되는 것 | 분리 안 되는 것 |
|---|---|---|
| `@Async` (소주제 1) | 실행 스레드 | 같은 JVM, 같은 메모리 |
| 이벤트 발행/구독 (소주제 2) | 코드 결합 | 같은 JVM, 같은 프로세스 |
| **메시지 큐 (소주제 3)** | **프로세스 / 머신** | (분리 안 되는 것 없음 — 통째로 분리) |

소주제 3은 **진짜 프로세스 단위 분리** 를 다룬다.

### 그림으로

```
[게시판 애플리케이션 프로세스]                [알림 서비스 프로세스]
   ┌────────────────────────────┐              ┌────────────────────────────┐
   │  CommentService              │              │  AlertConsumer              │
   │  → Producer가 메시지 발행      │   네트워크    │  → Consumer가 메시지 수신    │
   │                             │ ────────→    │  → 알림 발송                  │
   └────────────────────────────┘     ↑         └────────────────────────────┘
                                      │
                              ┌──────────────┐
                              │  메시지 브로커  │  ← 별도 프로세스
                              │   (RabbitMQ)  │
                              └──────────────┘
```

핵심 변화:
- 두 애플리케이션이 **다른 jar, 다른 프로세스**
- 둘 사이는 **네트워크 + 브로커** 를 통한 통신
- 메시지가 **브로커에 영속** — 한쪽이 잠시 죽어도 메시지는 보존

## 3. HTTP 직접 호출이 왜 부족한가

두 프로세스 통신의 가장 단순한 방법은 HTTP API 호출. 그러나 다음 한계가 있다.

| 문제 | HTTP 직접 호출 | 메시지 큐 |
|---|---|---|
| 응답 대기 | ❌ 블로킹 | ✅ 즉시 리턴 |
| 가용성 결합 | ❌ 둘 다 살아야 동작 | ✅ 한쪽 다운돼도 메시지 보존 |
| 처리 능력 결합 | ❌ 호출 즉시 처리 강요 | ✅ 큐가 버퍼 역할 |
| 메시지 영속성 | ❌ 호출 실패 = 메시지 유실 | ✅ 브로커가 디스크에 보존 |
| 시스템 격리 | ❌ 한쪽 장애 전파 | ✅ 격리됨 |

→ 메시지 큐의 본질: *"발행자와 소비자 사이에 영속적인 버퍼를 둬서 둘을 시간/가용성/처리 능력 측면에서 분리한다"*

## 4. RabbitMQ 핵심 구성 요소

RabbitMQ는 AMQP(Advanced Message Queuing Protocol) 표준을 구현한 메시지 브로커.

```
[Producer] ──→ [Exchange] ──(Binding)──→ [Queue] ──→ [Consumer]
     ↑              ↑                       ↑            ↑
   발행자        라우터/분배기              대기 상자     소비자
```

### 4.1. 다섯 구성 요소

| 요소 | 역할 |
|---|---|
| **Producer** | 메시지를 발행하는 측 (게시판 애플리케이션) |
| **Exchange** | 메시지를 받아 어느 큐로 보낼지 라우팅. Producer는 항상 Exchange로 발행 (직접 Queue에 X) |
| **Binding** | Exchange와 Queue를 연결하는 정적 규칙 |
| **Queue** | 메시지가 컨슈머에게 전달되기 전 적재되는 대기 상자 |
| **Consumer** | 메시지를 받아 처리하는 측 (알림 서비스) |

### 4.2. Exchange의 4가지 타입

라우팅 방식에 따라:

| 타입 | 동작 | 사용 예 |
|---|---|---|
| **Direct** | Routing Key가 정확히 일치하는 큐로 | `key=email` → `email` 큐 |
| **Fanout** | 바인딩된 모든 큐로 (브로드캐스트) | 알림 이벤트 → 이메일 큐 + SMS 큐 + 로그 큐 |
| **Topic** | Routing Key 패턴 매칭 (와일드카드) | `user.*.created` 패턴 매칭 |
| **Headers** | 메시지 헤더 값 기반. 드물게 사용 | — |

### 4.3. Fanout vs Topic — 헷갈리는 두 타입

기능적으로 Topic이 Fanout을 포함할 수 있지만(`#` 패턴으로 모든 메시지 받기), **의미가 다르다**:

- **Fanout** — *"이 사건에 모든 컨슈머가 똑같이 관심"* — 메시지가 한 종류
- **Topic** — *"다양한 사건 중 각 컨슈머가 일부만 관심"* — 메시지가 여러 종류

본 학습 시나리오(댓글 알림)는 메시지 한 종류 → Fanout이 자연스러움.

## 5. Spring + RabbitMQ 통합

### 5.1. 의존성

`build.gradle.kts`:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-amqp")
```

### 5.2. 연결 설정

`application.properties`:
```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

- `5672` — AMQP 프로토콜 포트
- `15672` — 관리 콘솔 HTTP 포트 (브라우저 접속용)

### 5.3. RabbitMQConfig — Exchange/Queue/Binding 빈 등록

```java
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "comment.event.exchange";
    public static final String QUEUE_NAME = "comment.email.queue";

    @Bean
    public FanoutExchange commentEventExchange() {
        return new FanoutExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue commentEmailQueue() {
        return new Queue(QUEUE_NAME);
    }

    @Bean
    public Binding commentEmailBinding(FanoutExchange exchange, Queue queue) {
        return BindingBuilder.bind(queue).to(exchange);
    }
}
```

이 빈들은 단순한 자바 객체이지만, Spring이 시작 시 RabbitMQ 서버에 *"이 이름의 Exchange/Queue를 만들어달라"* 라고 선언한다. DB 테이블 이름처럼, 문자열이 RabbitMQ 서버 내부의 식별자가 된다.

### 5.4. 메시지 컨버터 — JSON 사용 권장

Spring AMQP의 기본 메시지 컨버터는 **자바 ObjectInputStream 직렬화**를 쓴다. 이는 두 가지 문제가 있다:

1. **보안 위험** — 자바 ObjectInputStream은 임의 클래스를 역직렬화할 수 있어 *deserialization gadget* 공격에 취약. Spring AMQP는 보호 차원에서 *"허용된 클래스만 역직렬화"* 정책을 강제하므로, 별도 설정 없이는 `SecurityException` 발생.

2. **언어 종속성** — 자바 직렬화는 다른 언어와 호환 불가. MSA 환경에서는 부적합.

→ **JSON 컨버터로 전환** 이 표준 권장.

```java
@Bean
public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}

@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                      MessageConverter messageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(messageConverter);
    return template;
}
```

→ Producer 발행 시 객체 → JSON, Consumer 수신 시 JSON → 객체로 자동 변환.

### 5.5. Producer — RabbitTemplate으로 발행

```java
@Component
public class CommentNotificationListener {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CommentCreatedEvent event) {
        if (event.postAuthorId().equals(event.commentAuthorId())) {
            return;
        }

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_NAME,  // Exchange 이름
            "",                              // routing key (Fanout이라 무시)
            event                            // 메시지 객체
        );
    }
}
```

`convertAndSend`:
- 메시지를 자동으로 직렬화 (JsonConverter 설정했으면 JSON으로)
- Exchange로 발송 → Exchange가 Binding에 따라 Queue로 분배

### 5.6. Consumer — `@RabbitListener` 로 수신

```java
@Component
public class CommentEmailNotificationConsumer {

    private final CommentNotificationService commentEmailNotificationService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleCommentNotification(CommentCreatedEvent event) {
        commentEmailNotificationService.notifyNewComment(
            event.postAuthorEmail(),
            event.commentUsername(),
            event.commentContent()
        );
    }
}
```

`@RabbitListener`:
- Spring이 시작 시 listener container를 만들어 큐를 polling
- 메시지 도착 시 자동으로 메서드 호출
- 메시지를 역직렬화 후 메서드 파라미터로 전달

## 6. 전체 흐름 — 댓글 작성 시 일어나는 일

```
1. [게시판 프로세스] HTTP POST /comments → Controller → CommentService.createComment()
        ↓
2. @Transactional 시작
3. Comment 엔티티 저장
4. publishEvent(CommentCreatedEvent)
        ↓
5. @Transactional commit
        ↓
6. @TransactionalEventListener(AFTER_COMMIT) → CommentNotificationListener.handle()
        ↓
7. 본인 댓글이면 return (스킵)
8. RabbitTemplate.convertAndSend(EXCHANGE_NAME, "", event)
   → 이벤트 객체가 JSON으로 직렬화되어 RabbitMQ로 전송
        ↓
9. [RabbitMQ 서버]
   Exchange(comment.event.exchange) → Binding 따라 → Queue(comment.email.queue) 적재
   메시지가 디스크에 영속됨
        ↓
10. [Consumer 측 — 실무에선 별도 프로세스, 본 학습에선 같은 프로세스]
    @RabbitListener container가 큐에서 메시지 수신
    → CommentEmailNotificationConsumer.handleCommentNotification()
        ↓
11. CommentNotificationService.notifyNewComment(...) 호출
        ↓
12. @Async("notificationExecutor") 발동
    → notify-N 스레드로 비동기 위임
        ↓
13. sleep 1.5초 + 메일 발송 로그
```

→ 세 가지 도구(이벤트, 메시지 큐, @Async)가 각자 다른 차원의 역할로 협력하는 구조.

## 7. 주의점과 함정

본 학습 중 부딪힌 실무 함정들 정리.

### 7.1. 자바 직렬화 보안 정책

Spring AMQP 기본 컨버터(`SimpleMessageConverter`)는 자바 ObjectInputStream을 사용하며, 보안 정책에 의해 임의 클래스 역직렬화를 거부한다. 해결책 둘:
- `spring.amqp.deserialization.trust-all=true` (임시, 보안상 비추)
- **JSON 컨버터로 전환** (권장)

### 7.2. 메시지 큐의 영속성

RabbitMQ는 메시지를 **디스크에 영속** 시킨다. 애플리케이션이 죽어도 메시지는 큐에 남는다. 장점이지만 함정:
- 메시지 형식 변경 시 (예: 자바 직렬화 → JSON) **옛 형식의 메시지가 큐에 남아 있어** 새 컨슈머가 처리 실패
- 해결: 호환성 유지 (양 형식 처리) 또는 큐 비우기 (`Purge`)

### 7.3. 무한 재시도 vs Fatal 처리

처리 실패 시 RabbitMQ는 메시지를 다시 큐로 돌려놓아 재시도. 일반 예외는 무한 반복 위험. Spring AMQP는 영리하게:
- `MessageConversionException` (변환 실패) → **Fatal** 로 분류, 즉시 폐기
- 다른 예외 → requeue (재시도)

실무에서는 **Dead Letter Queue (DLQ)** 설정으로 N번 실패 시 별도 큐로 옮겨 분석.

### 7.4. Connection의 lazy 동작

`RabbitTemplate` 만 사용하는 경우 (Producer-only) connection이 **첫 발행 시점까지 lazy** 하게 만들어진다. `@RabbitListener` 가 등록되면 시작 즉시 connection 생성 (큐 polling 필요).

→ Producer-only일 때 시작 로그에 RabbitMQ 관련 메시지가 없어도 정상.

## 8. 다른 도구와의 비교 — Kafka

RabbitMQ와 자주 비교되는 Kafka는 **본질적으로 다른 도구**다.

| | RabbitMQ | Kafka |
|---|---|---|
| 본질 | 메시지 브로커 (작업 큐) | 분산 로그 (스트리밍) |
| 메시지 모델 | "전달되면 사라짐" | "기록되어 남아 있음" |
| 적합 시나리오 | 작업 큐, 복잡한 라우팅, 소~중 규모 | 대용량 스트리밍, 이벤트 소싱, 메시지 재처리 |

자세한 비교는 [RabbitMQ vs Kafka](QA_RabbitMQ_vs_Kafka.md) 참조.

본 학습 시나리오(댓글 알림)는 작업 큐 성격이라 RabbitMQ가 자연스러운 선택.

## 9. 정리

- 메시지 큐는 *"발행자와 소비자 사이의 영속적 버퍼"* — 프로세스 단위 분리를 가능하게 함
- 같은 JVM 안의 이벤트 패턴(소주제 2)과 달리, **시스템 격리, 메시지 영속성, 독립 스케일링** 을 제공
- RabbitMQ의 기본 구조: Producer → Exchange → Binding → Queue → Consumer
- Exchange 타입은 4가지 (Direct/Fanout/Topic/Headers). 시나리오에 맞춰 선택
- Spring 통합은 `spring-boot-starter-amqp` + `RabbitMQConfig` + `RabbitTemplate` + `@RabbitListener` 로 단순
- **JSON 메시지 컨버터** 가 표준 권장 (자바 직렬화의 보안 위험 회피, 언어 호환성)
- 메시지 큐는 영속적이라는 점이 강점이자 함정 — 호환성 깨지는 변경 시 주의
- Kafka는 다른 카테고리의 도구. 기능 집합이 아닌 *시나리오 적합도* 로 선택

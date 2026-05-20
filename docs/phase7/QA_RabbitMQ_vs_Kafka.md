# RabbitMQ vs Kafka

메시지 큐 카테고리에서 가장 자주 언급되는 두 도구. 같은 *비동기 통신* 카테고리에 있지만 **본질적 추상화 모델이 다른** 별개 도구. 한쪽이 다른 쪽의 상위호환이 아님.

## 1. 본질의 차이 — 한 줄 비유

> **RabbitMQ는 "메시지를 전달하는 우체국". Kafka는 "이벤트를 기록하는 로그 저장소".**

| | RabbitMQ | Kafka |
|---|---|---|
| 본질 | 메시지 브로커 (Message Broker) | 분산 로그 (Distributed Log) |
| 메시지 모델 | "전달되면 사라짐" | "기록되어 남아 있음" |
| 비유 | 우편물 — 받는 순간 끝 | 신문 — 구독자가 자기 페이스로 읽고, 과거호도 다시 볼 수 있음 |

## 2. 개념 매칭표

| RabbitMQ | Kafka | 비고 |
|---|---|---|
| Producer | Producer | 동일 개념 |
| **Exchange** | (없음) | Kafka는 라우터 없이 Producer가 Topic 직접 지정 |
| **Queue** | **Topic** | 메시지가 쌓이는 곳. 단 동작 방식이 다름 |
| **Binding** | (없음) | Topic-Consumer 직접 연결 |
| Routing Key | **Partition Key** | Kafka에선 메시지를 어느 partition에 둘지 결정 |
| Consumer | Consumer | 동일 개념 |
| (없음) | **Partition** | Topic의 분할 단위 (Kafka 특유) |
| (없음) | **Consumer Group** | 컨슈머 그룹화 (Kafka 특유) |
| (없음) | **Offset** | 컨슈머가 읽은 위치 (Kafka 특유) |
| 메시지 ack | Offset commit | 처리 완료 표시 방식의 차이 |
| 메시지 삭제 (처리 후) | 메시지 보관 (시간/크기 기반) | 결정적 차이 |

## 3. 같은 시나리오의 코드 비교 — 댓글 알림 발송

### 3.1. 의존성

| RabbitMQ | Kafka |
|---|---|
| `implementation("org.springframework.boot:spring-boot-starter-amqp")` | `implementation("org.springframework.kafka:spring-kafka")` |

### 3.2. application.properties

**RabbitMQ**:
```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

**Kafka**:
```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=comment-notification-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.study.board.event
```

→ Kafka 쪽에서 **Consumer Group ID** 와 직렬화 방식 명시 필요.

### 3.3. 인프라 설정 클래스

**RabbitMQ**:
```java
@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE_NAME = "comment.event.exchange";
    public static final String QUEUE_NAME = "comment.email.queue";

    @Bean public FanoutExchange commentEventExchange() { ... }
    @Bean public Queue commentEmailQueue() { ... }
    @Bean public Binding commentEmailBinding(...) { ... }
    @Bean public MessageConverter jsonMessageConverter() { ... }
    @Bean public RabbitTemplate rabbitTemplate(...) { ... }
}
```

**Kafka**:
```java
@Configuration
public class KafkaConfig {
    public static final String TOPIC_NAME = "comment-created-topic";

    @Bean
    public NewTopic commentCreatedTopic() {
        return TopicBuilder.name(TOPIC_NAME)
                .partitions(3)        // 파티션 수
                .replicas(1)          // 복제본 수
                .build();
    }
}
```

→ **Exchange/Queue/Binding 셋이 사라지고 Topic 하나** 로 단순해짐. 대신 **partition** 개념 등장.

### 3.4. Producer

**RabbitMQ**:
```java
rabbitTemplate.convertAndSend(
    RabbitMQConfig.EXCHANGE_NAME,
    "",                        // Fanout이라 빈 routing key
    event
);
```

**Kafka**:
```java
kafkaTemplate.send(KafkaConfig.TOPIC_NAME, event);
//              ↑ Topic 이름 (Exchange 없음)
```

→ **Exchange를 거치지 않고 Topic으로 직접**. 더 단순. 다만 라우팅 유연성은 떨어짐.

### 3.5. Consumer

**RabbitMQ**:
```java
@RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
public void handleCommentNotification(CommentCreatedEvent event) { ... }
```

**Kafka**:
```java
@KafkaListener(
    topics = KafkaConfig.TOPIC_NAME,
    groupId = "comment-notification-group"   // Consumer Group 명시 필수
)
public void handleCommentNotification(CommentCreatedEvent event) { ... }
```

→ **groupId** 명시가 필수. 같은 그룹의 컨슈머들은 메시지를 분배해서 받음.

## 4. Kafka 특유의 4가지 핵심 개념

### 4.1. Topic — Queue와 비슷하지만 결정적 차이

```
[RabbitMQ Queue]
  메시지 들어옴 → 적재 → 컨슈머가 받음 → ★ 큐에서 제거
  
[Kafka Topic]
  메시지 들어옴 → 적재 → 컨슈머가 읽음 → ★ 그대로 남아 있음
                                          (보관 기간 동안)
```

**의미**:
- 컨슈머는 *"내가 어디까지 읽었나"* 의 offset만 관리
- 같은 메시지를 **다른 그룹의 컨슈머가 또 읽을 수 있음** (브로드캐스트 효과)
- 컨슈머가 오프셋을 되돌려 **과거 메시지를 다시 읽기** 가능

→ Kafka의 *"분산 로그"* 본질. 메시지가 기록물처럼 남는 구조.

### 4.2. Partition — Topic의 분할

```
[Topic "comment-created-topic"]
  ├── Partition 0: 메시지 0, 3, 6, 9, ...
  ├── Partition 1: 메시지 1, 4, 7, 10, ...
  └── Partition 2: 메시지 2, 5, 8, 11, ...
```

**의미**:
- **병렬 처리 단위** — 컨슈머 N개가 파티션 N개를 나눠 동시 처리
- 각 파티션 내에선 **순서 보장**, 파티션 간엔 순서 보장 안 됨
- **Partition Key** 로 같은 키의 메시지는 항상 같은 파티션 (순서 보장 필요한 케이스)

예: 사용자별 알림이 같은 순서로 처리되어야 한다면 `userId`를 partition key로 사용.

### 4.3. Consumer Group — 컨슈머의 협력 단위

```
[Topic with 3 partitions]
  Partition 0 ──┐
  Partition 1 ──┼─→ Consumer Group "email-group"
  Partition 2 ──┘     ├── Consumer A → Partition 0
                      ├── Consumer B → Partition 1
                      └── Consumer C → Partition 2

                   → Consumer Group "analytics-group"
                      └── Consumer X → Partition 0, 1, 2 (혼자 다 받음)
```

**규칙**:
- **한 그룹 내**: 각 파티션은 그룹 내 *오직 한 컨슈머* 만 처리 (작업 분배)
- **다른 그룹 간**: 독립적으로 같은 메시지를 모두 받음 (브로드캐스트)

→ Producer 한 명이 발행하면, 알림 그룹은 분배 처리하고, 분석 그룹은 별도로 통째 처리. **둘이 동시에 같은 메시지를 다른 목적으로 사용**.

### 4.4. Offset — 컨슈머의 현재 위치

```
Partition 0: [msg0][msg1][msg2][msg3][msg4][msg5][msg6][msg7] → ...
                                        ↑
                            Consumer A의 offset (4까지 읽음)
```

**의미**:
- Consumer가 자기 offset을 commit (broker에 저장)
- 컨슈머 재시작 시 마지막 offset부터 재개
- offset을 과거로 되돌리면 **메시지 재처리** 가능

→ RabbitMQ는 메시지가 처리되면 사라지므로 *"과거 메시지 다시 처리"* 가 어렵다. Kafka의 가장 강력한 차별점.

## 5. 각자가 강한 시나리오

### 5.1. Kafka가 자연스러운 곳

| 시나리오 | Kafka가 더 좋은 이유 |
|---|---|
| **대용량 스트리밍** (초당 수만~수십만 건) | 높은 처리량 설계 |
| **여러 시스템이 같은 이벤트를 다른 목적으로** (알림 + 분석 + 추천) | Consumer Group으로 독립 구독 |
| **메시지 재처리 필요** (장애 복구, 로직 변경 후 과거 데이터 재처리) | Offset 되돌림 |
| **이벤트 소싱** (모든 상태 변경을 이벤트로 기록) | 메시지 영속 |
| **데이터 파이프라인** (서비스 → DB → 데이터 웨어하우스) | 표준 도구 |

### 5.2. RabbitMQ가 자연스러운 곳

| 시나리오 | RabbitMQ가 더 좋은 이유 |
|---|---|
| **작업 큐** (요청 받아 처리 후 삭제) | 처리 후 메시지 삭제가 자연스러움 |
| **복잡한 라우팅** (Topic Exchange로 패턴 매칭) | Exchange의 유연성 |
| **우선순위 큐, TTL, DLQ** | 큐 단위 세밀한 제어 |
| **작은~중간 규모** (초당 수백~수천 건) | 가볍고 단순 |
| **Request-Response 패턴** | reply queue 지원 |

## 6. 전체 비교 정리표

| 측면 | RabbitMQ | Kafka |
|---|---|---|
| 본질 | 메시지 브로커 (작업 큐) | 분산 로그 (스트리밍 플랫폼) |
| 메시지 모델 | "전달되면 사라짐" | "기록되어 남아 있음" |
| 처리 단위 | Queue | Topic + Partition |
| 컨슈머 협력 | 같은 Queue 구독 (자동 분배) | Consumer Group |
| 라우팅 | Exchange 타입 + Binding | Producer가 Topic 직접 지정 |
| 메시지 순서 보장 | 큐 단위 | Partition 단위 |
| 메시지 재처리 | 어려움 | 쉬움 (offset 되돌림) |
| 다중 컨슈머 그룹 (브로드캐스트) | Fanout Exchange 사용 | Consumer Group 분리로 자연스러움 |
| 강점 | 라우팅 유연성, 가벼움 | 처리량, 영속, 재처리 |
| 적합 규모 | 소~중 (초당 수천 건 수준) | 대규모 (초당 수만~수십만 건) |
| 학습 곡선 | 낮음 | 높음 |
| 운영 부담 | 가벼움 | 무거움 (ZooKeeper/KRaft 등) |

## 7. 도구 선택 시 사고 흐름

본 학습에서 RabbitMQ를 선택한 이유를 정리하면, 향후 선택 시 같은 사고법이 유용하다.

```
1. "메시지 1개 = 처리 1번" 인가? (작업 큐 성격)
   YES → RabbitMQ 유력
   NO (스트림 / 다중 구독) → Kafka 검토

2. 처리량이 초당 수천 건을 넘는가?
   YES → Kafka 검토
   NO → RabbitMQ 충분

3. 메시지 재처리(과거 데이터 다시 보기)가 필요한가?
   YES → Kafka 강력 추천
   NO → RabbitMQ로 충분

4. 같은 사건을 여러 시스템이 독립적으로 받아야 하는가?
   YES (3개 이상의 독립 시스템) → Kafka 자연스러움
   NO (1~2개 컨슈머) → RabbitMQ 충분

5. 운영 부담을 줄이고 싶은가?
   YES → RabbitMQ
   NO (이미 Kafka 운영 인프라 있음) → Kafka
```

본 학습 시나리오(댓글 알림)는 모든 질문에 *RabbitMQ 쪽* — 시나리오 적합도 100%.

## 8. 학습 노트 — Kafka가 RabbitMQ의 "상위호환"이 아닌 이유

기능 집합으로만 보면 Kafka가 RabbitMQ를 흉내낼 수 있는 부분이 있다 (모든 큐를 `#` 패턴으로 받게 하면 Fanout 비슷한 효과 등).

그러나 **본질적 메타포가 다르다**:
- Kafka는 *기록* 을 위한 도구 — *"이 사건이 일어났다"* 라는 사실의 영속적 보존
- RabbitMQ는 *전달* 을 위한 도구 — *"이 작업을 처리해달라"* 라는 요청의 전송

자연스러운 도구는 *기능* 보다 *사용 의도* 와 *추상화 적합도* 로 결정한다.

## 9. 정리

- 두 도구는 같은 *비동기 통신* 카테고리이지만 **본질적 추상화가 다른 별개 도구**
- Kafka는 *분산 로그*, RabbitMQ는 *메시지 브로커*
- 기능적으로 일부 겹치지만 *적합한 시나리오* 가 다름
- 본 학습 시나리오 (댓글 알림) 는 RabbitMQ가 자연스러움
- 시스템이 커져 *대용량 스트리밍, 다중 구독, 재처리, 이벤트 소싱* 등이 필요해지면 Kafka로 전환 검토
- 도구 선택은 *기능 집합* 이 아니라 *시나리오 적합도* 로 결정

# `@EventListener` 어댑터 메커니즘과 이벤트 타입 추론

## 질문 배경

Phase 7 정리 리뷰 중 영역 B(이벤트 발행/구독)에서 두 가지 의문이 떠올랐다.

1. *"`@EventListener` 가 붙은 메서드를 스프링이 처리할 때, 어댑터로 감싼다고 했는데 — 어댑터는 프록시와 같은 건가? 다르다면 무엇이 다른가? 메서드 시그니처를 ApplicationListener 로 변환한다는 게 메서드 이름을 바꾼다는 뜻인가? 그게 어떻게 가능한가?"*
2. *"이벤트 클래스(`CommentCreatedEvent`)에는 아무 어노테이션도 없고, 리스너 클래스에도 이벤트 타입을 명시하는 장치가 없는데 스프링은 어느 리스너가 어느 이벤트를 받는지 어떻게 아는가? 메서드 파라미터가 자동으로 등록되는 건가?"*

이 둘은 *"`@EventListener` 가 작동하는 진짜 메커니즘"* 을 묻는 본질적 질문이다.

---

## 핵심 답변 1 — 어댑터는 *새 래퍼 객체*, 메서드 변환이 아님

### 멀티캐스터의 제약

스프링의 이벤트 멀티캐스터(`ApplicationEventMulticaster`)는 **단 한 가지 인터페이스만 호출할 줄 안다**:

```java
public interface ApplicationListener<E extends ApplicationEvent> {
    void onApplicationEvent(E event);
}
```

멀티캐스터 내부 코드는 대략 이 형태:

```java
for (ApplicationListener listener : matchedListeners) {
    listener.onApplicationEvent(event);   // 이 한 줄밖에 모름
}
```

다른 형태의 메서드를 호출하는 법은 모른다.

### 학습자 메서드는 ApplicationListener가 아니다

개발자가 작성하는 코드:

```java
@Component
class CommentNotificationListener {
    @EventListener
    public void handleCommentCreated(CommentCreatedEvent event) {  // ApplicationListener 아님
        // ...
    }
}
```

이 클래스는 `ApplicationListener` 를 구현하지 않는다. 메서드 이름도 `onApplicationEvent` 가 아닌 임의의 이름. **멀티캐스터가 이 메서드를 직접 호출할 방법이 없다.**

### 어댑터 — 변환이 아니라 *새 객체 생성*

스프링이 시작될 때 `EventListenerMethodProcessor` 가 모든 `@EventListener` 메서드를 스캔하면서, 각 메서드마다 **새로운 객체** 를 만든다:

```java
// 스프링 내부에서 만드는 객체 (단순화한 의사 코드)
class ApplicationListenerMethodAdapter implements ApplicationListener<ApplicationEvent> {

    private final Object bean;        // 개발자가 만든 빈 (CommentNotificationListener 인스턴스)
    private final Method method;      // 개발자가 만든 메서드 (handleCommentCreated)
    private final Class<?> eventType; // 이 리스너가 받는 이벤트 타입 (CommentCreatedEvent)

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        // 멀티캐스터는 이 메서드만 호출함
        // 어댑터가 내부에서 개발자의 진짜 메서드를 리플렉션으로 호출
        method.invoke(bean, event);
    }
}
```

핵심:

- **이 어댑터는 진짜로 존재하는 별개의 객체** (개발자가 만든 `CommentNotificationListener` 와 별도로 존재).
- 어댑터는 `ApplicationListener` 인터페이스를 *구현* 함 → 멀티캐스터가 호출 가능.
- 어댑터의 `onApplicationEvent()` 내부는 **리플렉션** (`Method.invoke()`)으로 개발자의 진짜 메서드를 호출.

→ **개발자 메서드는 그대로 있다.** 이름이 바뀌거나 시그니처가 바뀌지 않는다. 변하는 건 *"멀티캐스터가 호출할 수 있는 새로운 객체가 추가로 만들어진다"* 는 점.

### 리플렉션이 가능하게 해주는 메커니즘

자바의 `Method` 객체는 *"메서드 자체"* 를 1급 객체로 다룰 수 있게 해준다:

```java
Class<?> clazz = bean.getClass();
Method m = clazz.getMethod("handleCommentCreated", CommentCreatedEvent.class);
m.invoke(bean, event);   // bean.handleCommentCreated(event) 와 동일한 효과
```

이렇게 *"메서드 이름과 시그니처를 런타임에 알아내고 호출하는 기능"* 이 **리플렉션(Reflection)**. 어댑터는 이 기능을 활용해 *"개발자가 어떤 이름의 메서드를 만들었든, 일단 onApplicationEvent 호출이 들어오면 그걸 개발자 메서드 호출로 변환"* 한다.

---

## 핵심 답변 2 — 프록시 vs 어댑터 비교

### 프록시 (`@Async`, `@Transactional`, `@Cacheable`)

```
[CommentService]
       ↓ emailService.sendNotification(...) 호출
[EmailService 프록시]   ← 같은 인터페이스 EmailService
       ↓ 부가 기능(스레드 풀에 위임) 추가 후
[EmailService 진짜 객체]
       ↓
   진짜 메서드 실행
```

- 같은 인터페이스를 *유지* 하면서 *통과* 시킴.
- 호출 흐름: **호출자 → 프록시 → 진짜**.
- 목적: 메서드 호출 앞뒤에 부가 기능 추가.

### 어댑터 (`@EventListener`)

```
[멀티캐스터]
       ↓ listener.onApplicationEvent(event) 호출 (ApplicationListener 인터페이스)
[ApplicationListenerMethodAdapter]   ← ApplicationListener 인터페이스 구현
       ↓ 리플렉션으로 method.invoke(bean, event)
[CommentNotificationListener.handleCommentCreated(event)]   ← 다른 인터페이스/시그니처
```

- 멀티캐스터가 *기대하는 인터페이스* (`ApplicationListener`)를 어댑터가 구현해서, 멀티캐스터와 개발자 메서드 사이의 **시그니처 차이를 메워줌**.
- 호출 흐름: **멀티캐스터 → 어댑터 → 개발자 객체**.
- 목적: 다른 인터페이스를 *변환*.

### 비유

| 패턴 | 비유 |
|---|---|
| 프록시 | **통역사** — 한국어 → 한국어로 전달, 다만 중간에 *"~씨께서 말씀하시길..."* 같은 형식적 멘트 추가 |
| 어댑터 | **220V → 110V 변환 플러그** — 한쪽 모양과 다른 쪽 모양을 물리적으로 변환해서, 본래는 꽂힐 수 없는 기기를 사용 가능하게 함 |

---

## 핵심 답변 3 — 이벤트 타입 정보의 출처 = 메서드 파라미터

### 의문점

`CommentCreatedEvent` 는 *그냥 레코드*:

```java
public record CommentCreatedEvent(
    Long postAuthorId,
    Long commentAuthorId,
    String postAuthorEmail,
    String postAuthorPhone,
    String commentUsername,
    String commentContent
) {}
```

어떤 어노테이션도 없다. 리스너 클래스에도 *이벤트 타입을 명시하는 어떤 장치도 없음*:

```java
@Component
class CommentNotificationListener {
    @EventListener
    public void handleCommentCreated(CommentCreatedEvent event) { ... }
}
```

그런데 스프링은 *"이 리스너가 CommentCreatedEvent를 받는다"* 는 사실을 어떻게 아는가?

### 답: 메서드 파라미터 타입 = 이벤트 타입

스프링이 시작될 때 `EventListenerMethodProcessor` 가 다음 일을 한다:

```
1. 모든 빈을 순회
2. 각 빈의 메서드 중 @EventListener 가 붙은 것을 찾음
3. 발견된 메서드의 파라미터 타입을 리플렉션으로 읽음
   → Method.getParameterTypes()
4. 이 파라미터 타입을 "이 리스너가 받는 이벤트 타입" 으로 결정
5. 어댑터(ApplicationListenerMethodAdapter)를 생성하고 이벤트 타입 정보를 같이 저장
6. 멀티캐스터에 어댑터 등록
```

즉, **메서드 파라미터 타입이 곧 이벤트 타입의 출처**. 어디 명시할 필요 없음 — 메서드 시그니처 자체가 메타데이터.

### 매칭 동작

발행된 이벤트가 멀티캐스터에 도착하면:

```java
Class<?> eventClass = event.getClass();   // 예: CommentCreatedEvent.class

for (ApplicationListenerMethodAdapter adapter : allAdapters) {
    if (adapter.eventType.isAssignableFrom(eventClass)) {
        // 어댑터의 이벤트 타입이 이 이벤트를 받을 수 있는가? (부모 타입 매칭 포함)
        adapter.onApplicationEvent(event);
    }
}
```

`isAssignableFrom` 을 쓰기 때문에 **상속 관계도 자동 매칭**. 예: 리스너가 `Object` 를 받으면 모든 이벤트를, `ApplicationEvent` 를 받으면 모든 ApplicationEvent 하위 타입을 받음.

---

## 핵심 답변 4 — 파라미터 개수 규칙

| 파라미터 개수 | 가능 여부 | 동작 |
|---|---|---|
| **1개** | ✅ 기본 | 그 파라미터 타입이 이벤트 타입 |
| **0개** | ⚠️ 조건부 | `@EventListener(MyEvent.class)` 로 어노테이션 속성에 타입을 명시하면 가능 |
| **2개 이상** | ❌ 불가 | 스프링 시작 시 예외 발생 |

### 0개 파라미터 예시

```java
@EventListener(CommentCreatedEvent.class)   // 어노테이션 속성에 타입 명시
public void onCommentCreated() {            // 파라미터 0개
    // 이벤트 발생 사실만 알면 충분, 내용은 안 봄
    log.info("새 댓글 발생");
}
```

이벤트 *발생 사실* 만 신호로 받고 내용엔 관심 없을 때 사용.

### 어노테이션 속성으로 여러 타입 받기

```java
@EventListener({CommentCreatedEvent.class, PostCreatedEvent.class})
public void onAnyContentCreated(Object event) {   // Object로 받음
    // 둘 중 어느 이벤트든 처리
}
```

어노테이션 속성으로 *받을 이벤트 타입들* 을 명시할 수 있음. 이 경우 파라미터 타입은 두 이벤트의 공통 부모(또는 Object)로 받으면 됨.

대부분의 경우 **1개 파라미터 방식이 가장 깔끔**해서 본 프로젝트도 그렇게 작성됨.

---

## 보너스 — 조건부 처리 (`condition` 속성)

SpEL 표현식으로 *"이 이벤트의 어떤 조건이 맞을 때만 처리"* 가 가능:

```java
@EventListener(condition = "#event.postAuthorId != #event.commentAuthorId")
public void notifyNewComment(CommentCreatedEvent event) {
    // 자기 글에 자기가 댓글 단 경우는 자동으로 스킵됨
}
```

본 학습에서는 메서드 안에서 if 로 체크하는 방식을 썼지만, 이런 *선언적 방식* 도 가능. 어떤 방식이 더 좋은지는 케이스 바이 케이스 — 조건이 단순하면 어노테이션 방식이 깔끔, 복잡하면 메서드 안에서 if 로 명시하는 게 가독성 좋음.

---

## 전체 흐름 다이어그램

```
[Spring 부팅 시점]
─────────────────────────────────────────────────────────
1. EventListenerMethodProcessor가 모든 빈 스캔
2. @EventListener 메서드 발견:
     - 빈: CommentNotificationListener
     - 메서드: handleCommentCreated(CommentCreatedEvent)
3. 메서드 파라미터 타입 리플렉션으로 읽음
     → 이벤트 타입 = CommentCreatedEvent.class
4. ApplicationListenerMethodAdapter 생성
     [bean=CommentNotificationListener 인스턴스,
      method=handleCommentCreated,
      eventType=CommentCreatedEvent.class]
5. 멀티캐스터에 어댑터 등록

[런타임 — 이벤트 발행 시점]
─────────────────────────────────────────────────────────
1. CommentService:
     publisher.publishEvent(new CommentCreatedEvent(...));
2. 멀티캐스터 도착
3. 모든 어댑터 순회:
     adapter.eventType(=CommentCreatedEvent) .isAssignableFrom(event.getClass()) ?
4. 매칭된 어댑터에:
     adapter.onApplicationEvent(event) 호출
5. 어댑터 내부:
     method.invoke(bean, event)  // 리플렉션으로 진짜 메서드 호출
6. 개발자 메서드 실행:
     handleCommentCreated(event)
```

---

## 한 줄 요약

> 스프링의 이벤트 시스템은 **"리플렉션 + 어댑터 패턴"** 으로 *"개발자가 쓰고 싶은 형태의 메서드"* 를 *"멀티캐스터가 호출할 수 있는 표준 형태(ApplicationListener)"* 로 다리 놓는다. 이벤트 타입 정보는 **메서드 파라미터 타입** 에서 자동 추출된다.

---

## 관련 문서

- [[AOP와 프록시 메커니즘]] — 프록시의 일반적 동작
- [[프록시 JDK vs CGLIB]] — 프록시 구현 방식의 두 가지

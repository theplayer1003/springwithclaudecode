# 문제 사항 배경

비동기 클래스를 작성하고 테스트 중 발생.
먼저 비동기 코드와 기대했던 테스트 코드, 시나리오에 대해 정리함.

테스트 방법으로 비동기로 다른 스레드가 일을 하는지 직접 보기 위해 통합 테스트를 채택했고 @MockitoSpyBean, 스파이 객체를 동원하기로 함.

스파이 객체란 진짜 객체를 mockito 가 감싸서 만든 프록시 객체임.
기본 동작들을 그대로 실행하며 동시에 호출 기록을 남겨 이를 verify 에 동원할 수 있음.
필요하다면 stubbing 으로 특정 동작을 다르게 수행하도록 조작이 가능

```java
doAnswer(invocation -> {
            capturedThreadName.set(Thread.currentThread().getName());
            invocation.callRealMethod();
            latch.countDown();
            return null;
        }).when(commentEmailNotificationService).notifyNewComment(any(), any(), any());
```

doAnswer 는 다르게 수행하고자 하는 동작 정의, when 절이 모킹할 대상임.

이제 notifyNewComment 가 호출되면 진짜 메서드 대신 invocation 람다가 실행됨
우리는 이 메서드를 실행시켜서 현재 지금 작업이 수행되고 있는 스레드의 이름이 뭔지 체크할거임.
왜냐면 프로덕트 코드를 작성할때 스레드 이름을 notify- 라고 짓도록 했으니까 이름을 확인하면 지정한 다른 스레드가 일을 하는게 맞는지 볼 수 있음.
(이 부분 이해가 안됨. 실제 메서드 호출은 invocation.callReaMethod() 에서 호출한다고 했는데 그러면 아직 호출 쪽 스레드 아님?
그러면 일을 시킬 스레드의 이름이 찍힐텐데 원래 계획과 다른거 아닌지? callRealMethod() 안에 장치가 있어야 의도대로 notify 스레드 이름이 찍힐거 같음.)
이제 invocation.callREalMethod() 로 진짜 호출해야할 메서드, notifyNewComment 를 호출함.
해당 메서드는 진짜 작업이 일어나진 않고 모사를 위해 sleep 1.5 초가 걸려있음. 이 슬립이 notify- 스레드에서 일어날거임.
비동기 메서드기 때문에 새로운 스레드에 작업을 던지고 invocation 이 수행되던 스레드는 latch.countDown() 으로 넘어감.
countDown 은 이름 그대로 카운트를 줄이는 것. latch 값이 1이었고, 이것은 1개의 작업이 수행되기를 기대한다는 뜻.
작업이 수행되었으니 카운트 1을 줄여 0이 됨.

doAnswer 를 빠져나와 latch.await 에 도착. 이 메서드는 3초동안 latch가 0이 되길 기다려보겠단 뜻임.
일을 시킨 스레드는 일을 던지고 바로 countdown 했으니 이때 카운트가 0이 되어 latch 에 알람(?) 이 가고 값은 true 가 됨.
assert문에서 true 임을 확인하고 테스트 성공 종료.

이게 원래 기대했던 테스트임.

# 하지만 문제점

테스트가 의도대로 진행되지 않았음. 이유는 @Async 의 방식과 Mockito 의 충돌.
(궁금한게 이 충돌이란게 각각의 라이브러리는 각자가 할일을 각자의 방식대로 진행하지 서로 호환 같은걸 고려하지 않기 때문에 이런 충돌이 일어나는것?
뭔가 표준 같은게 없어서 A 는 A 대로 이렇게 작업을 구현해놔야지~ 했고 B 는 저렇게 작업을 구현해놔야지~ 했는데 이번처럼 서로 겹치는 영역에서 조율이 안되는 그런건지?)

@Async 를 그냥 클래스에 달아두면 Spring 은 자기 디폴트 값인 CGLIB 로 프록시를 만듬.
mockito 는 모키토 대로 스파이를 위한 프록시 객체를 만듬. 이 과정에서 둘이 충돌함.

구조상 spring 에게 우선권이 있음. 원본 객체에 @Async 를 감지하고 진짜 객체를 상속한 프록시(자식) 객체를 작성함.
그리고 모키토가 스파이를 만드려고 함. 이미 스프링이 프록시로 원본 객체를 덮어놨기 때문에 프록시를 spy 로 인식함.
그치만 원본 객체가 아니라 stubbing 등록에 실패함.(CGLIB 클래스를 mockito 가 가로채기 어렵단게 무슨 뜻? 어째서 그렇게 되는거임?)

테스트가 실행되고 commentEmailNotificationService 의 메서드가 호출되면 스프링 프록시가 이를 받음.
@Async 동작 원리에 의해 TaksExecutor 한테 작업을 주고... 우리가 작성한대로 일을 처리함.
stubbing 등록이 안되어 있기 때문에 doAnswer 의 람다가 실행되지 않고 latch 의 카운트가 줄어들지 않음.
테스트가 실패함.



시나리오 A — [spy] → [@Async 프록시] → [원본] (spy가 바깥)
시나리오 B — [@Async 프록시] → [spy] → [원본] (@Async가 바깥)

두 시나리오를 제시해주셨는데 그러면 실제 애플리케이션이 실행되고 두가지 경우의 수가 있단 뜻인가요?
둘중 어떤 형태가 될지 모르는건가요?
일단 callRealMethod 문제의 해결책 또한 interface 화에 있단건 알겠습니다.
다만 @SpyBean/@MockitoSpyBean + @Async 조합에서 보통 시나리오 A 에 가깝게 된다는게 무슨 표현인지 모르겠어요.
보통이라뇨? 확률이란 뜻인가요, 아니면 어떤지 정확히는 모른단 뜻인건가요?

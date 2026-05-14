# 프록시 — JDK Dynamic Proxy vs CGLIB

Spring AOP가 프록시 기반으로 동작한다는 것은 [AOP/프록시 메커니즘](QA_AOP_프록시_메커니즘.md)에서 다뤘다. 이 문서는 한 단계 깊이 들어가, **그 프록시가 실제로 어떻게 만들어지는가** — 즉 자바에서 동적으로 프록시 객체를 만드는 두 가지 방법인 **JDK Dynamic Proxy**와 **CGLIB**를 정리한다.

## 1. 왜 두 가지가 필요한가

프록시는 *"진짜 객체와 동일한 타입으로 보이면서 메서드 호출을 가로채는 객체"* 다. 자바에서 이걸 동적으로 만들려면 **새 클래스를 런타임에 생성**해야 한다. 그런데 자바의 타입 시스템에서 *"동일한 타입으로 보이게 만드는 방법"* 은 두 가지 뿐이다.

1. **인터페이스를 구현**하는 클래스를 만든다 → 같은 인터페이스 타입으로 보임
2. **클래스를 상속**하는 자식 클래스를 만든다 → 부모 클래스 타입으로 보임

→ 전자가 **JDK Dynamic Proxy**, 후자가 **CGLIB**의 접근 방식이다.

## 2. JDK Dynamic Proxy

자바 표준 라이브러리(`java.lang.reflect.Proxy`)가 제공하는 프록시 생성 메커니즘. **인터페이스 기반.**

### 동작 원리

```
대상: 인터페이스 X를 구현한 클래스 A의 객체

[Proxy.newProxyInstance(...)]
        ↓
자바 표준 라이브러리가 X를 구현하는 새 클래스를 동적 생성
        ↓
새 클래스의 모든 메서드는 InvocationHandler.invoke(...)로 위임
        ↓
프록시 인스턴스 반환 (X 타입으로 사용 가능)
```

생성된 프록시 클래스 모양 (대략):
```java
final class $Proxy0 implements X {
    private InvocationHandler h;
    
    public void method(Object arg) {
        h.invoke(this, methodReference, new Object[]{arg});
    }
}
```

### 핵심 제약

- **반드시 인터페이스가 있어야 함** — 대상 객체가 어떤 인터페이스 X를 구현하고 있어야 함
- 프록시는 X 타입으로만 사용 가능 (A 타입으로 캐스팅 불가)
- final 메서드 제약 없음 (인터페이스 메서드는 final 불가니까)

### 장점

- **자바 표준 메커니즘** — 다른 라이브러리(Mockito 등)와의 호환성이 좋음
- 추가 라이브러리 의존 없음
- 메모리 효율 (클래스가 인터페이스 메서드 시그니처만큼만 생성)

### 단점

- 인터페이스가 없는 클래스에는 사용 불가

## 3. CGLIB (Code Generation Library)

오픈소스 라이브러리. **클래스 상속 기반.**

원래는 별도 라이브러리였지만 현재는 Spring이 fork하여 `spring-core`에 내장. 별도 의존성 추가 없이 Spring 프로젝트에서 사용 가능.

### 동작 원리

```
대상: 일반 클래스 A의 객체 (인터페이스 없어도 OK)

[CGLIB Enhancer]
        ↓
A를 상속한 새 자식 클래스 A$$EnhancerByCGLIB$$xxxx를 바이트코드 조작으로 생성
        ↓
자식 클래스는 부모(A)의 모든 메서드를 override
        ↓
override한 메서드는 MethodInterceptor.intercept(...)로 위임
        ↓
프록시 인스턴스 반환 (A 타입으로 사용 가능)
```

생성된 프록시 클래스 모양 (대략):
```java
class A$$EnhancerByCGLIB$$xxxx extends A {
    private MethodInterceptor interceptor;
    
    @Override
    public void method(Object arg) {
        interceptor.intercept(this, method, args, methodProxy);
        // methodProxy.invokeSuper(...) 로 부모(원본)의 메서드를 호출
    }
}
```

### 핵심 제약

- **상속 불가능한 경우 사용 불가**
  - `final` 클래스 — 상속 자체가 불가
  - `final` 메서드 — 오버라이드 불가
  - `private` 메서드 — 외부에서 호출 불가, 가로채기도 불가
- 기본 생성자가 호출되어야 함 (객체 인스턴스화 과정)

### 장점

- 인터페이스 없는 일반 클래스에도 사용 가능
- 부모 클래스 타입 그대로 사용 가능 (캐스팅 자유로움)

### 단점

- **비표준 메커니즘** — 다른 동적 프록시 라이브러리(Mockito 등)와의 충돌 가능성
- 클래스 생성 비용이 JDK Proxy보다 큼
- 자바 모듈 시스템 / 최신 JDK 버전에서 reflection 제약 부딪힘

## 4. Spring의 자동 선택 기준

Spring AOP는 다음 규칙으로 프록시 방식을 자동 선택한다:

```
대상 빈에 인터페이스가 있는가?
   ├─ YES → JDK Dynamic Proxy 사용
   └─ NO  → CGLIB 사용
```

단, Spring Boot 2.0부터는 **CGLIB가 기본**이 되도록 변경됐다 (`spring.aop.proxy-target-class=true`가 기본값). 이유:
- 자체 클래스 캐스팅을 자주 하는 코드에서 JDK Proxy가 ClassCastException 일으키는 사례
- CGLIB의 성능 격차가 줄어듦

→ Spring Boot 환경에서는 기본적으로 CGLIB로 만들어진다. 그러나 학습자 프로젝트의 `CommentNotificationService` 출력에 `$$SpringCGLIB$$0` 이 보인 것도 이 때문이다.

JDK Proxy를 강제하려면:
```properties
spring.aop.proxy-target-class=false
```

다만 이 설정은 **대상 빈에 인터페이스가 있어야만** 의미가 있다. 인터페이스가 없으면 어차피 CGLIB를 쓸 수밖에 없다.

## 5. 실무에서 이 차이가 중요한 순간

### (1) 테스트 — Mockito와의 호환성

Mockito는 자체적으로 동적 프록시(보통 ByteBuddy 사용)를 만들어 spy/mock 메서드 호출을 가로챈다. Spring 빈이 이미 CGLIB로 감싸진 상태에서 Mockito가 또 그 위를 감싸려 하면 두 메커니즘이 충돌하기 쉽다.

→ 이 충돌 사례는 별도 문서 [Mockito Spy와 Async 충돌](QA_Mockito_Spy와_Async_충돌.md)에서 다룬다.

→ **개선 시도**: 빈을 인터페이스 기반으로 분리 → Spring이 JDK Proxy를 쓰게 됨 → Mockito와 호환성 향상이 *기대*되지만, 학습 중 실제 검증 결과 **interface 분리만으로는 spy + @Async 충돌이 완전히 해결되지 않는 환경이 존재**한다. 본질적으로 통한 우회법은 위 별도 문서의 4.4절 참조.

### (2) 자기 호출 함정 — 두 방식 모두에 적용됨

`this.method()` 호출이 프록시를 우회하는 문제는 JDK Proxy와 CGLIB 모두에 적용된다. 자세한 내용은 [AOP/프록시 메커니즘](QA_AOP_프록시_메커니즘.md) 참조.

### (3) final / private 메서드

CGLIB을 사용한다면 `@Async` / `@Transactional` 등이 final 또는 private 메서드에는 적용되지 않음을 인지해야 한다.

## 6. 빠른 비교표

| 항목 | JDK Dynamic Proxy | CGLIB |
|---|---|---|
| 자바 표준 여부 | ✅ 표준 (java.lang.reflect.Proxy) | ❌ 비표준 (오픈소스) |
| 인터페이스 필요 | ✅ 필수 | ❌ 불필요 |
| 동작 방식 | 인터페이스 구현체 동적 생성 | 자식 클래스 동적 생성 |
| 프록시 타입 | 구현한 인터페이스 타입 | 부모 클래스 타입 |
| final 클래스 | OK | ❌ 불가 |
| final 메서드 | OK (인터페이스에는 final 불가) | ❌ 가로채기 불가 |
| private 메서드 | OK (인터페이스 메서드는 public) | ❌ 가로채기 불가 |
| 다른 라이브러리와 호환 | 높음 | 충돌 가능 |
| Spring Boot 2.0+ 기본 | (수동 설정 필요) | ✅ 기본 |

## 7. 정리

- 자바에서 동적 프록시를 만드는 방법은 두 가지: **JDK Dynamic Proxy (인터페이스 구현)** 와 **CGLIB (클래스 상속)**
- JDK는 자바 표준, CGLIB은 비표준 라이브러리
- Spring Boot 2.0+ 는 CGLIB를 기본으로 사용. 인터페이스가 있어도 CGLIB가 우선
- final / private 메서드는 CGLIB로 가로챌 수 없다 → AOP 어노테이션이 무시됨
- Mockito 등 다른 동적 프록시 라이브러리와의 호환성은 JDK Proxy가 좋음 — 같은 표준 메커니즘 위에서 동작하기 때문
- 인터페이스 분리는 단순한 코딩 스타일이 아니라, **프록시 호환성과 mockito 통합에 실질적 영향을 미치는 설계 결정**이다

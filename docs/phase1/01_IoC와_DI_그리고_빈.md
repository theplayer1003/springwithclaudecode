# IoC(제어의 역전), DI(의존성 주입), 그리고 빈(Bean)

스프링을 이해하려면 이 세 가지 개념을 반드시 먼저 이해해야 합니다.
이 개념들은 스프링의 존재 이유이자, 앞으로 작성할 모든 코드의 기반이 됩니다.

---

## 1. 먼저 "의존성"이란?

코드에서 **의존성**이란, 어떤 클래스가 동작하기 위해 다른 클래스가 필요한 관계를 말합니다.

```java
public class PostController {
    private PostService postService = new PostService();
    
    public void createPost(String title) {
        postService.save(title);
    }
}
```

여기서 `PostController`는 `PostService` 없이는 동작할 수 없습니다.
→ **PostController는 PostService에 의존한다**고 표현합니다.

---

## 2. 직접 생성의 문제점

위 코드에서 `new PostService()`로 직접 객체를 만들면 어떤 문제가 생길까요?

### 문제 1: 변경이 어렵다

만약 `PostService`의 생성자가 바뀌면?

```java
// PostService가 DB 연결을 받도록 변경되었다면
public class PostService {
    public PostService(DatabaseConnection db) { ... }
}
```

이 경우 `PostController` 코드도 반드시 수정해야 합니다:

```java
public class PostController {
    // PostService가 바뀔 때마다 여기도 바뀌어야 한다!
    private PostService postService = new PostService(new DatabaseConnection());
}
```

`PostService`를 사용하는 곳이 10군데라면? 10군데 모두 수정해야 합니다.

### 문제 2: 테스트가 어렵다

`PostController`를 테스트하고 싶은데, `PostService`가 실제 DB에 데이터를 저장한다면?
테스트할 때마다 DB에 데이터가 들어가게 됩니다.

"가짜 PostService"로 바꿔서 테스트하고 싶어도, `new PostService()`가 코드에 박혀있으면 바꿀 수 없습니다.

### 문제 3: 결합도가 높다

`PostController`가 `PostService`의 **구체적인 구현**을 직접 알고 있습니다.
이렇게 클래스들이 서로를 강하게 알고 있는 것을 **결합도가 높다**고 합니다.

결합도가 높으면:
- 하나를 바꾸면 연쇄적으로 다른 것도 바꿔야 함
- 코드 재사용이 어려움
- 유지보수가 힘들어짐

---

## 3. IoC — 제어의 역전 (Inversion of Control)

**"제어의 역전"**이란 이름이 어려워 보이지만 핵심은 간단합니다:

> **객체를 내가 직접 만들지 않고, 외부(프레임워크)가 만들어서 제공한다.**

### 일반적인 흐름 (제어가 나에게 있음)

```
PostController가 직접 PostService를 만든다
→ "내가 필요한 걸 내가 만든다"
```

### IoC 흐름 (제어가 프레임워크에 있음)

```
스프링이 PostService를 미리 만들어둔다
→ PostController가 필요하다고 하면 스프링이 넣어준다
→ "내가 필요한 걸 누군가 가져다준다"
```

콘솔 프로그램에서는 `main()` 메서드에서 개발자가 직접 모든 객체를 만들고 연결했습니다.
스프링에서는 이 "객체 생성과 연결"의 제어권이 스프링 프레임워크로 넘어갑니다.
그래서 **"제어의 역전"**입니다.

### 비유로 이해하기

**직접 요리하기 (일반적인 방식)**
- 장을 보고, 재료를 손질하고, 요리를 직접 한다
- 재료가 바뀌면 요리 방법도 바꿔야 한다

**레스토랑에서 주문하기 (IoC 방식)**
- "스테이크 주세요"라고만 말한다
- 어떤 재료를 쓰고, 어떻게 조리하는지는 주방(프레임워크)이 알아서 한다
- 재료가 바뀌어도 나는 그냥 주문만 하면 된다

---

## 4. DI — 의존성 주입 (Dependency Injection)

DI는 IoC를 구현하는 **구체적인 방법**입니다.

> **필요한 객체(의존성)를 외부에서 넣어주는(주입하는) 것**

### DI 적용 전 (직접 생성)

```java
public class PostController {
    private PostService postService = new PostService(); // 직접 만듬
}
```

### DI 적용 후 (외부에서 주입)

```java
public class PostController {
    private final PostService postService;
    
    // 생성자를 통해 외부에서 PostService를 받는다
    public PostController(PostService postService) {
        this.postService = postService;
    }
}
```

차이를 보세요:
- `new PostService()`가 사라졌습니다
- 대신 **생성자의 매개변수**로 `PostService`를 받습니다
- 누가 넣어주는지? → **스프링이 자동으로 넣어줍니다**

### DI의 3가지 방식

스프링에서 의존성을 주입하는 방법은 세 가지가 있습니다:

#### (1) 생성자 주입 (권장)

```java
@RestController
public class PostController {
    private final PostService postService;
    
    public PostController(PostService postService) {
        this.postService = postService;
    }
}
```

- `private final` → 한번 주입되면 변경 불가 (안전)
- 생성자가 하나면 `@Autowired` 생략 가능 (스프링이 자동 인식)
- **실무에서 가장 권장되는 방식**

#### (2) 필드 주입

```java
@RestController
public class PostController {
    @Autowired
    private PostService postService;
}
```

- 코드가 짧아 보이지만, `final`을 쓸 수 없고 테스트가 어려움
- **실무에서 권장하지 않는 방식**

#### (3) 세터 주입

```java
@RestController
public class PostController {
    private PostService postService;
    
    @Autowired
    public void setPostService(PostService postService) {
        this.postService = postService;
    }
}
```

- 선택적 의존성에 사용할 수 있지만, 대부분의 경우 생성자 주입이 더 좋음

**결론: 생성자 주입을 사용하세요.** 앞으로 이 프로젝트에서도 생성자 주입만 사용합니다.

---

## 5. 빈(Bean) — 스프링이 관리하는 객체

스프링이 만들고 관리하는 객체를 **빈(Bean)**이라고 부릅니다.

일반 자바에서는 개발자가 `new`로 객체를 만들었습니다.
스프링에서는 특정 어노테이션을 붙이면 스프링이 자동으로 객체를 만들어서 관리합니다.

### 빈으로 등록하는 어노테이션들

| 어노테이션 | 용도 | 예시 |
|-----------|------|------|
| `@Component` | 일반적인 빈 등록 | 유틸리티 클래스 등 |
| `@Controller` / `@RestController` | 웹 요청을 처리하는 클래스 | PostController |
| `@Service` | 비즈니스 로직을 담당하는 클래스 | PostService |
| `@Repository` | 데이터 접근을 담당하는 클래스 | PostRepository |

이 네 가지는 사실 기능적으로 거의 동일합니다. `@Service`, `@Repository`는 `@Component`의 특수한 형태입니다.
하지만 **역할을 명확히 구분**하기 위해 다르게 씁니다.

### 빈의 생명주기

```
1. 스프링 애플리케이션 시작
2. @Component, @Service 등이 붙은 클래스를 자동 탐색 (컴포넌트 스캔)
3. 해당 클래스의 객체(빈)를 생성
4. 의존성 주입 (DI) 수행
5. 애플리케이션 실행 중에는 빈을 재사용 (기본: 싱글톤)
6. 애플리케이션 종료 시 빈 소멸
```

### 싱글톤(Singleton)이란?

스프링은 기본적으로 각 빈을 **딱 하나만** 만듭니다.

```
PostController가 PostService를 요청 → 스프링이 PostService 빈을 줌
CommentController가 PostService를 요청 → 같은 PostService 빈을 줌
```

즉, `PostService` 객체가 하나만 존재하고 여러 곳에서 공유합니다.
이것이 **싱글톤 패턴**이며, 메모리를 절약하고 일관성을 유지할 수 있습니다.

---

## 6. 실제 동작 흐름 정리

우리가 만들 게시판 프로젝트의 흐름을 예로 들면:

```
[스프링 부트 시작]
    ↓
[컴포넌트 스캔] → PostController, PostService 등을 발견
    ↓
[빈 생성] → PostService 객체 생성, PostController 객체 생성
    ↓
[DI 수행] → PostController의 생성자에 PostService 빈을 주입
    ↓
[웹 서버 시작] → 요청을 받을 준비 완료
    ↓
[GET /posts 요청 들어옴]
    ↓
[PostController.getPosts() 실행]
    ↓
[PostController가 가지고 있는 PostService를 사용]
    ↓
[응답 반환]
```

---

## 7. 핵심 정리

| 개념 | 한 줄 요약 |
|------|-----------|
| **의존성** | A 클래스가 B 클래스 없이 동작할 수 없는 관계 |
| **IoC** | 객체 생성/관리의 제어권을 프레임워크에 넘기는 것 |
| **DI** | IoC를 구현하는 방법 — 필요한 객체를 외부에서 주입받는 것 |
| **빈** | 스프링이 만들고 관리하는 객체 |
| **싱글톤** | 빈은 기본적으로 하나만 만들어져서 공유됨 |
| **생성자 주입** | DI의 권장 방식 — 생성자 매개변수로 의존성을 받는 것 |

---

## 다음 주제
이 개념들을 이해한 후, 다음으로 **스프링 부트 프로젝트 구조와 REST API의 기본**을 학습합니다.

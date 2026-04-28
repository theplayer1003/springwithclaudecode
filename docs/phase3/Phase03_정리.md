# Phase 3: 계층 구조와 예외 처리

---

## 1. 왜 계층을 나누는가

Phase 2까지의 코드는 동작에 필요한 모든 로직이 Controller 하나에 몰려 있었다. 지금은 기능이 적어 문제가 없어 보이지만, 애플리케이션이 커질수록 다음 문제들이 드러난다.

- **가독성** — 메서드가 길어져 무슨 일이 일어나는지 한눈에 파악하기 어려움
- **테스트** — Controller는 외부 DB 등의 요소가 필요해 비즈니스 로직만 단독 테스트하기 힘듦
- **재사용성** — 같은 기능을 다른 곳에서 필요로 할 경우 코드를 재사용할 수 없음
- **변경 영향** — 모든 변경의 여파가 하나의 클래스에 집중됨

이를 해결하기 위해 역할별로 클래스를 분리한다.

---

## 2. 3계층 구조 (3-Tier Architecture)

```
Presentation Layer ↔ Business Layer ↔ Data Access Layer
  (Controller)         (Service)        (Repository)
```

| 계층         | 역할                                       |
|------------|------------------------------------------|
| Controller | 외부 요청을 받아 적절한 Service 메서드로 연결하는 진입점      |
| Service    | 실제 비즈니스 로직을 수행. Repository와 협력하여 데이터를 처리 |
| Repository | DB와의 데이터 접근을 담당                          |

클라이언트의 요청을 Controller가 받으면, Service에 로직 수행을 위임하고, Service는 Repository와 협력해 결과를 반환한다. Controller는 그 결과를 DTO로 포장해 클라이언트에게
응답한다.

---

## 3. DTO (Data Transfer Object)

DTO는 데이터를 운반하는 목적의 객체다. Phase 2에서는 Entity를 그대로 요청/응답에 사용했지만, 이는 보안과 결합도 문제를 유발한다.

### Response DTO — 나가는 데이터를 제한

Entity에는 클라이언트에게 노출하면 안 되는 정보(내부 연관관계, 민감한 필드 등)가 있을 수 있다. Response DTO는 필요한 정보만 담아 내보낸다.

### Request DTO — 들어오는 데이터를 제한

Entity로 요청을 직접 받으면 세 가지 문제가 생긴다.

**1) 보안 — 조작되면 안 되는 필드까지 받을 수 있음**

게시글 생성에는 title과 content만 필요한데, Entity로 받으면 Jackson이 모든 필드를 set 한다. 클라이언트가 id나 createdAt을 조작해서 보내도 그대로 반영될 위험이 있다.
Request DTO에는 필요한 필드만 정의하므로 나머지는 무시된다.

**2) 검증 규칙의 분리 — 상황별로 다른 검증이 필요**

생성 시에는 author가 필수이고, 수정 시에는 author를 변경 불가로 해야 할 수 있다. Entity 하나에 이 두 가지 규칙을 모두 표현할 수 없다. `PostCreateRequest`,
`PostUpdateRequest`처럼 DTO를 분리하면 상황별 검증이 가능하다.

**3) 결합도 — Entity 변경이 API에 영향**

Entity에 필드를 추가하거나 이름을 바꾸면 클라이언트가 보내는 JSON 형식도 바뀌어야 한다. DTO가 중간에 있으면 Entity가 바뀌어도 DTO만 수정하여 클라이언트에 미치는 영향을 줄일 수 있다.

### Entity는 검증을 안 하는가?

DTO와 Entity의 검증은 역할이 다르다.

- **DTO의 검증** — 클라이언트 입력이 형식적으로 올바른가? (빈 문자열인지, 길이 초과인지)
- **Entity의 검증** — 비즈니스 규칙을 만족하는가? (예: 게시글 상태가 DRAFT인데 공개 요청이 왔는가)

콘솔 프로그램에서 도메인 객체가 했던 검증이 사라진 것이 아니라, 입력 검증과 비즈니스 검증으로 분리된 것이다.

---

## 4. 예외 처리

Phase 2의 코드는 ResponseEntity로 HTTP 상태 코드만 반환했다. 존재하지 않는 게시글을 조회하면 404를 반환했지만, 게시글이 없어서인지 URL이 잘못된 것인지 클라이언트는 구분할 수 없었다.

### 커스텀 예외

Java의 Unchecked Exception(RuntimeException)을 상속받아 비즈니스 상황에 맞는 예외 클래스를 만든다. Service 계층에서 문제가 발생하면 이 예외를 던진다.

### 글로벌 예외 처리 (@RestControllerAdvice)

커스텀 예외가 던져지면 Spring은 기본적으로 500 에러를 반환한다. `@RestControllerAdvice`로 Handler 클래스를 구현하면 던져진 예외를 가로채 원하는 형식의 응답으로 변환할 수 있다.
ErrorResponse라는 응답 형식 클래스를 함께 사용하면 클라이언트는 실패 원인에 대한 구체적인 정보를 얻게 된다.

예외 처리의 핵심은 **관심사의 분리**다. Service는 "못 찾았다"는 사실을 예외로 던지기만 하고, 그것을 어떤 HTTP 상태 코드와 어떤 형식으로 응답할지는 Handler가 결정한다.

---

## 5. 입력값 검증 (Bean Validation)

이미 발생한 오류를 처리하는 것뿐 아니라, 애초에 잘못된 입력이 들어오지 않도록 제한할 수도 있다.

### 검증 어노테이션

Request DTO의 필드에 검증 어노테이션을 붙여 규칙을 선언한다.

| 어노테이션              | 대상       | 설명                               |
|--------------------|----------|----------------------------------|
| `@NotNull`         | 모든 타입    | null을 허용하지 않는다                   |
| `@NotBlank`        | 문자열      | null, "", 공백만 있는 문자열을 허용하지 않는다   |
| `@NotEmpty`        | 문자열, 컬렉션 | null, 빈 값을 허용하지 않는다 (공백 문자열은 허용) |
| `@Size(min, max)`  | 문자열, 컬렉션 | 길이와 크기에 제한을 둔다                   |
| `@Min`, `@Max`     | 숫자       | 최소, 최대값 제한을 둔다                   |
| `@Email`           | 문자열      | 이메일 형식을 검증한다                     |
| `@Pattern(regexp)` | 문자열      | 정규표현식 패턴으로 검증한다                  |

어노테이션에 `message` 속성으로 검증 실패 시 에러 메시지를 함께 선언할 수 있다.

### @Valid로 검증 실행

Controller 메서드의 `@RequestBody` 앞에 `@Valid`를 붙이면, JSON을 DTO로 역직렬화할 때 검증 규칙에 따라 검증을 수행한다. 실패하면
`MethodArgumentNotValidException`이 자동 발생하며, 이 역시 GlobalExceptionHandler에서 잡아 적절한 에러 응답으로 변환한다.

---

## 6. @Transactional

Service 메서드에 `@Transactional`을 붙여 해당 메서드가 트랜잭션으로 처리되어야 함을 명시한다.

- **쓰기 메서드** (create, update, delete) → `@Transactional`
- **읽기 메서드** (get, search) → `@Transactional(readOnly = true)`

읽기 메서드는 `@Transactional` 없이도 동작하지만, `readOnly = true`를 붙이면 다음 이점이 있다.

1. **성능** — Dirty Checking을 건너뛰어 불필요한 비교 작업을 생략
2. **의도 전달** — 코드를 읽는 사람에게 "이 메서드는 데이터를 변경하지 않는다"고 명확히 알림
3. **안전성** — 실수로 Entity를 수정해도 DB에 반영되지 않음

---

## 7. JPA 콜백 어노테이션

콜백(Callback)이란 "나중에 특정 시점이 되면 불러달라"는 뜻이다. 개발자가 직접 호출하는 것이 아니라, JPA가 적절한 시점에 해당 메서드를 자동으로 실행한다.

대표적인 활용 예시로 게시글의 생성/수정 시간 기록이 있다. 개발자가 매번 시간을 직접 설정할 수도 있지만, `@PrePersist`와 `@PreUpdate`를 통해 자동화하면 Entity를 수정하는 곳이 아무리
많아져도 누락이 불가능하다.

| 어노테이션          | 호출 시점             | 활용 예시                      |
|----------------|-------------------|----------------------------|
| `@PrePersist`  | INSERT 직전 (최초 저장) | createdAt, updatedAt 자동 기록 |
| `@PostPersist` | INSERT 직후         | 생성 이벤트 로깅, 알림 발송           |
| `@PreUpdate`   | UPDATE 직전 (변경 감지) | updatedAt 자동 갱신            |
| `@PostUpdate`  | UPDATE 직후         | 변경 이력 기록                   |
| `@PreRemove`   | DELETE 직전         | 삭제 전 연관 데이터 정리, 삭제 로그 기록   |
| `@PostRemove`  | DELETE 직후         | 외부 시스템에 삭제 알림              |

**주의:** 콜백 메서드는 JPA가 자동으로 호출하므로 Service에서 직접 호출하면 안 된다 (2번 실행됨).

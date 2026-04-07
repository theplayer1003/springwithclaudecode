# 02. 프로젝트 구조와 REST API — Q&A

## Q1: spring-boot-starter-web에 자동으로 포함되는 라이브러리에는 어떤 것들이 있나요?

`spring-boot-starter-web` 하나를 추가하면 실제로는 이런 라이브러리들이 딸려온다:

| 라이브러리                   | 역할                                                     |
|-------------------------|--------------------------------------------------------|
| **Tomcat**              | 내장 웹 서버. 별도 서버 설치 없이 `java -jar`로 바로 실행 가능하게 해줌        |
| **Spring MVC**          | `@RestController`, `@GetMapping` 등 웹 요청을 처리하는 핵심 프레임워크 |
| **Jackson**             | 자바 객체 ↔ JSON 자동 변환. `@RequestBody`와 응답 JSON 변환을 담당     |
| **Spring Web**          | HTTP 요청/응답, `RestTemplate` 등 웹 통신 기본 기능                |
| **Hibernate Validator** | `@Valid`, `@NotNull` 등 입력값 검증 (Phase 3에서 사용 예정)        |

앞으로 Phase가 진행되면서 다른 starter도 추가하게 된다:

- `spring-boot-starter-data-jpa` → DB 연동 (Phase 2)
- `spring-boot-starter-security` → 인증/인가 (Phase 4)
- `spring-boot-starter-test` → 테스트 (이미 포함됨)

---

## Q2: @RequestBody의 PostRequest는 빈으로 등록되어 있어야 하나요?

**결론: PostRequest는 빈이 아니다. Jackson이 요청마다 아예 처음부터 객체를 새로 만들어준다.**

|               | 빈 (Bean)                      | @RequestBody 객체        |
|---------------|-------------------------------|------------------------|
| **누가 만드는가**   | 스프링 컨테이너가 시작 시 1개 생성          | Jackson이 요청마다 매번 새로 생성 |
| **몇 개 존재하는가** | 1개 (싱글톤)                      | 요청마다 1개씩 (수천 개일 수 있음)  |
| **어노테이션**     | `@Component`, `@Service` 등 필요 | 아무 어노테이션도 필요 없음        |
| **용도**        | 애플리케이션 로직 담당                  | 데이터를 담는 그릇             |

변환 과정:

```
클라이언트가 JSON 전송: { "title": "제목", "content": "내용" }
    ↓
Jackson이 PostRequest 객체를 new로 생성
    ↓
JSON의 "title" 값 → setTitle("제목") 호출
JSON의 "content" 값 → setContent("내용") 호출
    ↓
완성된 PostRequest 객체를 컨트롤러 메서드에 전달
```

Jackson 라이브러리가 JSON의 키 이름과 클래스의 필드 이름을 매칭해서 자동으로 객체를 만들어주는 것. 스프링의 DI/빈과는 무관한 별도의 메커니즘이다.

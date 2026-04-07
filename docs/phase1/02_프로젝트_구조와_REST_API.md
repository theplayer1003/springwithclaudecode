# 스프링 부트 프로젝트 구조와 REST API 기본

---

## 1. 프로젝트 구조

현재 생성된 프로젝트의 디렉토리 구조입니다:

```
board/
├── build.gradle              ← 프로젝트 설정 및 의존성 관리
├── settings.gradle           ← 프로젝트 이름 설정
├── gradlew / gradlew.bat     ← Gradle 실행 스크립트 (Linux/Mac / Windows)
├── gradle/
│   └── wrapper/              ← Gradle을 별도 설치 없이 사용하게 해주는 도구
├── src/
│   ├── main/
│   │   ├── java/com/study/board/
│   │   │   └── BoardApplication.java    ← 애플리케이션 진입점
│   │   └── resources/
│   │       ├── application.properties   ← 애플리케이션 설정 파일
│   │       ├── static/                  ← 정적 파일 (CSS, JS, 이미지)
│   │       └── templates/               ← HTML 템플릿 (우리는 사용 안 함)
│   └── test/
│       └── java/com/study/board/
│           └── BoardApplicationTests.java  ← 테스트 코드
```

### 각 파일의 역할

#### `build.gradle` — 프로젝트의 설계도

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.study'        // 조직/회사 구분 (보통 도메인 역순)
version = '0.0.1-SNAPSHOT' // 프로젝트 버전

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21) // Java 21 사용
    }
}

dependencies {
    // 웹 서버(Tomcat) + REST API 기능
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // 테스트 도구
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

콘솔 프로그램에서는 `.java` 파일을 직접 `javac`로 컴파일했지만, 스프링 프로젝트에서는:

- **Gradle**이 의존성(외부 라이브러리)을 자동으로 다운로드하고
- 컴파일, 테스트, 패키징을 모두 처리합니다

`spring-boot-starter-web` 하나만 추가하면 웹 서버에 필요한 수십 개의 라이브러리가 자동으로 포함됩니다. 이것이 스프링 부트의 "starter" 개념입니다.

#### `BoardApplication.java` — 진입점

```java
package com.study.board;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BoardApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoardApplication.class, args);
    }
}
```

- `@SpringBootApplication` — 이 하나의 어노테이션이 세 가지 역할을 합니다:
    - `@SpringBootConfiguration` — 스프링 부트 설정 클래스임을 표시
    - `@EnableAutoConfiguration` — 추가된 라이브러리를 보고 자동으로 설정 (예: web starter가 있으면 Tomcat 자동 설정)
    - `@ComponentScan` — 이 클래스가 있는 패키지(`com.study.board`)와 하위 패키지에서 `@Component`, `@Controller` 등을 자동 탐색

- `SpringApplication.run()` — 이 한 줄이 실행되면:
    1. 스프링 컨테이너(ApplicationContext) 생성
    2. 빈 스캔 및 등록
    3. 내장 Tomcat 서버 시작 (기본 포트: 8080)
    4. 요청 대기 상태

#### `application.properties` — 설정 파일

현재는 비어있지만, 여기에 다양한 설정을 추가할 수 있습니다:

```properties
# 서버 포트 변경
server.port=8080
# 로그 레벨 설정
logging.level.root=INFO
```

스프링 부트는 이 파일이 없어도 기본값으로 동작합니다 (Convention over Configuration).

---

## 2. REST API란?

### API란?

**API(Application Programming Interface)**는 프로그램끼리 소통하는 규칙입니다.

콘솔 프로그램에서는 사용자가 키보드로 직접 입력했지만, 웹에서는:

- **프론트엔드**(웹 브라우저, 모바일 앱)가 **백엔드**(서버)에 데이터를 요청
- 백엔드가 데이터를 응답

이 요청-응답 규칙이 API입니다.

### REST란?

**REST(Representational State Transfer)**는 API를 설계하는 방식 중 하나입니다.
핵심 규칙은 간단합니다:

> **URL은 "무엇을(자원)", HTTP 메서드는 "어떻게(행위)"를 나타낸다.**

### HTTP 메서드

| 메서드      | 의미      | 예시                   |
|----------|---------|----------------------|
| `GET`    | 조회 (읽기) | 게시글 목록 보기, 특정 게시글 보기 |
| `POST`   | 생성 (쓰기) | 새 게시글 작성             |
| `PUT`    | 전체 수정   | 게시글 전체 내용 수정         |
| `PATCH`  | 부분 수정   | 게시글 제목만 수정           |
| `DELETE` | 삭제      | 게시글 삭제               |

### 게시판 API 설계 예시

```
GET    /posts       → 게시글 목록 조회
GET    /posts/1     → 1번 게시글 조회
POST   /posts       → 새 게시글 생성
PUT    /posts/1     → 1번 게시글 수정
DELETE /posts/1     → 1번 게시글 삭제
```

URL에는 **명사**(posts)를 쓰고, 행위는 **HTTP 메서드**로 표현합니다.
`/createPost`, `/deletePost` 같은 동사형 URL은 REST 방식이 아닙니다.

### JSON — 데이터 형식

REST API에서 데이터를 주고받을 때는 주로 **JSON** 형식을 사용합니다:

```json
{
  "id": 1,
  "title": "첫 번째 게시글",
  "content": "안녕하세요!",
  "createdAt": "2026-04-02T10:30:00"
}
```

자바의 `Map`이나 객체와 비슷한 키-값 구조입니다. 스프링 부트는 자바 객체를 JSON으로 자동 변환해줍니다.

---

## 3. 스프링에서 REST API 만들기

### 핵심 어노테이션

#### `@RestController`

```java

@RestController
public class PostController {
    // 이 클래스의 모든 메서드는 JSON을 반환한다
}
```

- `@Controller` + `@ResponseBody`의 합체
- `@Controller`만 쓰면 HTML 페이지를 반환하지만, `@RestController`는 **데이터(JSON)**를 반환
- 우리는 프론트엔드 없이 데이터만 다루므로 `@RestController`를 사용

#### `@RequestMapping`과 HTTP 메서드 어노테이션

```java

@RestController
@RequestMapping("/posts")  // 이 컨트롤러의 모든 URL은 /posts로 시작
public class PostController {

    @GetMapping           // GET /posts → 목록 조회
    public List<Post> getAllPosts() { ...}

    @GetMapping("/{id}")  // GET /posts/1 → 단건 조회
    public Post getPost(@PathVariable Long id) { ...}

    @PostMapping          // POST /posts → 생성
    public Post createPost(@RequestBody PostRequest request) { ...}

    @PutMapping("/{id}")  // PUT /posts/1 → 수정
    public Post updatePost(@PathVariable Long id, @RequestBody PostRequest request) { ...}

    @DeleteMapping("/{id}") // DELETE /posts/1 → 삭제
    public void deletePost(@PathVariable Long id) { ...}
}
```

#### `@PathVariable` — URL 경로에서 값 추출

```java

@GetMapping("/{id}")
public Post getPost(@PathVariable Long id) {
    // GET /posts/42 요청 시 → id = 42
}
```

URL의 `{id}` 부분이 매개변수 `id`에 자동으로 들어옵니다.

#### `@RequestBody` — 요청 본문의 JSON을 객체로 변환

```java

@PostMapping
public Post createPost(@RequestBody PostRequest request) {
    // 클라이언트가 보낸 JSON이 PostRequest 객체로 자동 변환됨
}
```

클라이언트가 이런 JSON을 보내면:

```json
{
  "title": "제목",
  "content": "내용"
}
```

스프링이 자동으로 `PostRequest` 객체에 매핑합니다:

```java
public class PostRequest {
    private String title;
    private String content;
    // getter, setter
}
```

---

## 4. 요청-응답 흐름

클라이언트가 `GET /posts/1`을 요청했을 때의 전체 흐름:

```
[클라이언트] GET /posts/1 요청
    ↓
[Tomcat] HTTP 요청 수신
    ↓
[DispatcherServlet] URL과 매칭되는 컨트롤러 메서드 탐색
    ↓
[PostController.getPost(1)] 메서드 실행
    ↓
[Post 객체 반환]
    ↓
[HttpMessageConverter] Post 객체 → JSON 변환
    ↓
[클라이언트] JSON 응답 수신
```

`DispatcherServlet`은 스프링 MVC의 핵심으로, 모든 요청을 받아서 적절한 컨트롤러에 전달하는 역할을 합니다. 지금은 "스프링이 알아서 요청을 적절한 메서드로 연결해준다" 정도로 이해하면 됩니다.

---

## 5. HTTP 상태 코드

REST API에서 응답할 때 **상태 코드**도 함께 반환합니다:

| 코드                          | 의미             | 사용 예시          |
|-----------------------------|----------------|----------------|
| `200 OK`                    | 성공             | 조회 성공          |
| `201 Created`               | 생성 성공          | 게시글 생성 성공      |
| `204 No Content`            | 성공 (반환 데이터 없음) | 삭제 성공          |
| `400 Bad Request`           | 잘못된 요청         | 필수 값 누락        |
| `404 Not Found`             | 리소스 없음         | 존재하지 않는 게시글 조회 |
| `500 Internal Server Error` | 서버 오류          | 서버 내부 에러       |

스프링에서는 기본적으로 200을 반환하지만, `ResponseEntity`를 사용해서 상태 코드를 직접 지정할 수 있습니다:

```java

@PostMapping
public ResponseEntity<Post> createPost(@RequestBody PostRequest request) {
    Post post = postService.create(request);
    return ResponseEntity.status(201).body(post);
}
```

이 부분은 코딩할 때 더 자세히 다루겠습니다.

---

## 6. 핵심 정리

| 개념                              | 설명                                    |
|---------------------------------|---------------------------------------|
| **build.gradle**                | 프로젝트 의존성과 빌드 설정을 관리하는 파일              |
| **@SpringBootApplication**      | 자동 설정 + 컴포넌트 스캔 + 설정 클래스 역할           |
| **application.properties**      | 서버 포트, DB 연결 등 애플리케이션 설정              |
| **REST API**                    | URL(자원) + HTTP 메서드(행위)로 구성된 API 설계 방식 |
| **@RestController**             | JSON 데이터를 반환하는 컨트롤러                   |
| **@GetMapping, @PostMapping 등** | HTTP 메서드와 URL을 메서드에 매핑                |
| **@PathVariable**               | URL 경로에서 변수 추출                        |
| **@RequestBody**                | 요청 JSON을 자바 객체로 변환                    |
| **DispatcherServlet**           | 모든 요청을 받아 적절한 컨트롤러로 전달하는 핵심 컴포넌트      |

---

## 다음 주제

이 개념들을 이해한 후, 다음으로 **JSON 요청/응답 처리**에 대해 더 자세히 학습합니다.

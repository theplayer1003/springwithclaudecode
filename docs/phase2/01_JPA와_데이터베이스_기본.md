# JPA와 데이터베이스 기본

Phase 1에서는 `HashMap`에 데이터를 저장했습니다. 서버를 종료하면 데이터가 모두 사라졌죠.
실제 서비스에서는 **데이터베이스(DB)**에 데이터를 영구적으로 저장해야 합니다.

---

## 1. 데이터베이스란?

데이터를 구조적으로 저장하고 관리하는 시스템입니다.

### 관계형 데이터베이스 (RDBMS)

우리가 사용할 데이터베이스는 **관계형 데이터베이스**입니다. 데이터를 **테이블(표)** 형태로 저장합니다.

게시글 테이블 예시:

```
posts 테이블
+----+----------+----------+---------------------+
| id | title    | content  | created_at          |
+----+----------+----------+---------------------+
| 1  | 첫 글    | 안녕하세요 | 2026-04-02 10:00:00 |
| 2  | 두번째   | 반갑습니다 | 2026-04-02 11:00:00 |
+----+----------+----------+---------------------+
```

- **테이블** — 엑셀의 시트와 비슷. 하나의 데이터 종류를 담음 (게시글, 댓글 등)
- **행(Row)** — 하나의 데이터 레코드 (1번 게시글, 2번 게시글)
- **열(Column)** — 데이터의 속성 (id, title, content 등)
- **기본키(Primary Key)** — 각 행을 고유하게 식별하는 값. 보통 `id`

### SQL

데이터베이스를 조작하는 언어가 **SQL(Structured Query Language)**입니다.

```sql
-- 게시글 조회
SELECT * FROM posts WHERE id = 1;

-- 게시글 생성
INSERT INTO posts (title, content, created_at) VALUES ('제목', '내용', NOW());

-- 게시글 수정
UPDATE posts SET title = '수정된 제목' WHERE id = 1;

-- 게시글 삭제
DELETE FROM posts WHERE id = 1;
```

자바 코드에서 이런 SQL을 직접 작성해서 DB에 보낼 수 있습니다.
하지만 이 방식에는 문제가 있습니다.

---

## 2. SQL을 직접 쓰면 생기는 문제

### 문제 1: 반복적인 코드

게시글을 조회하려면:

```java
String sql = "SELECT * FROM posts WHERE id = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setLong(1, id);
ResultSet rs = stmt.executeQuery();

if (rs.next()) {
    Post post = new Post();
    post.setId(rs.getLong("id"));
    post.setTitle(rs.getString("title"));
    post.setContent(rs.getString("content"));
    post.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
}
```

단순히 게시글 하나를 조회하는데 이만큼의 코드가 필요합니다. 컬럼이 추가되면? 모든 SQL과 매핑 코드를 수정해야 합니다.

### 문제 2: 패러다임 불일치

자바는 **객체 지향** 언어입니다. 데이터를 객체로 다룹니다.
데이터베이스는 **테이블과 행**으로 데이터를 다룹니다.

이 둘의 사고방식이 다릅니다:

```
자바: post.getComments()        → 객체에서 바로 댓글에 접근
SQL:  SELECT * FROM comments    → 별도의 쿼리를 날려야 함
      WHERE post_id = 1
```

자바에서는 객체 안에 다른 객체를 담는 게 자연스럽지만, DB에서는 테이블이 분리되어 있어서 **JOIN**이라는 별도 연산이 필요합니다.

### 문제 3: DB에 종속

MySQL용으로 작성한 SQL이 PostgreSQL에서는 동작하지 않을 수 있습니다. DB를 바꾸면 SQL도 바꿔야 합니다.

---

## 3. ORM과 JPA

이런 문제들을 해결하기 위해 **ORM**이 등장했습니다.

### ORM (Object-Relational Mapping)

> **자바 객체와 데이터베이스 테이블을 자동으로 매핑해주는 기술**

ORM을 사용하면:

- SQL을 직접 작성하지 않아도 됨
- 자바 객체를 다루듯이 DB를 조작할 수 있음
- DB가 바뀌어도 코드 수정이 최소화됨

### JPA (Java Persistence API)

**JPA**는 자바 진영의 **ORM 표준 인터페이스(명세)**입니다.

JPA 자체는 "이렇게 동작해야 한다"는 규칙(인터페이스)일 뿐이고, 실제 구현체가 필요합니다. 가장 대표적인 구현체가 **Hibernate**입니다.

```
JPA (인터페이스/명세)
 └── Hibernate (구현체) ← 스프링 부트가 기본으로 사용
```

Phase 1에서 배운 DI 개념과 비슷합니다. 인터페이스(JPA)에 의존하고, 구현체(Hibernate)는 교체 가능합니다.

### Spring Data JPA

스프링에서는 JPA를 더 편하게 쓸 수 있도록 **Spring Data JPA**를 제공합니다.

```
개발자
 └── Spring Data JPA (편의 기능)
      └── JPA (표준 인터페이스)
           └── Hibernate (실제 구현)
                └── JDBC (DB 통신)
                     └── 데이터베이스
```

Spring Data JPA를 쓰면 SQL은 물론이고, 복잡한 JPA 코드도 작성할 필요가 거의 없습니다.

---

## 4. Entity — 테이블과 매핑되는 자바 클래스

JPA에서 **Entity**는 데이터베이스 테이블과 1:1로 매핑되는 자바 클래스입니다.

```java
@Entity                          // 이 클래스는 DB 테이블과 매핑된다
public class Post {

    @Id                          // 이 필드가 기본키(Primary Key)
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // ID 자동 증가
    private Long id;

    private String title;        // title 컬럼과 매핑

    private String content;      // content 컬럼과 매핑

    private LocalDateTime createdAt;  // created_at 컬럼과 매핑

    // 기본 생성자 필수 (JPA 요구사항)
    protected Post() {}

    // getter, setter
}
```

### 어노테이션 설명

| 어노테이션             | 역할                                           |
|-------------------|----------------------------------------------|
| `@Entity`         | 이 클래스를 DB 테이블과 매핑                            |
| `@Id`             | 기본키(Primary Key) 필드 지정                       |
| `@GeneratedValue` | 기본키 생성 전략. `IDENTITY`는 DB가 자동 증가             |
| `@Column`         | 컬럼 이름, 길이, NOT NULL 등 세부 설정 (생략 시 필드명 = 컬럼명) |

### 테이블 자동 생성

JPA는 Entity 클래스를 보고 **자동으로 테이블을 만들어줄 수 있습니다**:

```
Post 클래스          →     posts 테이블
├── Long id          →     id BIGINT (PK, AUTO_INCREMENT)
├── String title     →     title VARCHAR(255)
├── String content   →     content VARCHAR(255)
└── LocalDateTime    →     created_at TIMESTAMP
```

클래스 이름 `Post` → 테이블 이름 `posts` (소문자 + 복수형)
필드 이름 `createdAt` → 컬럼 이름 `created_at` (카멜케이스 → 스네이크케이스)

이 변환은 스프링 부트의 네이밍 전략이 자동으로 처리합니다.

---

## 5. Repository — 데이터 접근 계층

Phase 1에서는 `HashMap`에 직접 데이터를 넣고 꺼냈습니다.
JPA에서는 **Repository**가 이 역할을 합니다.

```java
public interface PostRepository extends JpaRepository<Post, Long> {
    // 끝! 이것만으로 CRUD가 모두 가능
}
```

`JpaRepository<Post, Long>`을 상속하면 다음 메서드가 자동으로 제공됩니다:

| 메서드              | 역할         | Phase 1 대응                        |
|------------------|------------|-----------------------------------|
| `save(post)`     | 저장 (생성/수정) | `posts.put(id, post)`             |
| `findById(id)`   | ID로 조회     | `posts.get(id)`                   |
| `findAll()`      | 전체 조회      | `new ArrayList<>(posts.values())` |
| `deleteById(id)` | ID로 삭제     | `posts.remove(id)`                |
| `existsById(id)` | 존재 여부 확인   | `posts.containsKey(id)`           |
| `count()`        | 전체 개수      | `posts.size()`                    |

**인터페이스만 만들면 구현체는 스프링이 자동 생성합니다.** 이것이 Spring Data JPA의 핵심 편의 기능입니다.

### 쿼리 메서드 — 메서드 이름으로 쿼리 생성

메서드 이름을 규칙에 맞게 작성하면 SQL 없이 쿼리가 자동 생성됩니다:

```java
public interface PostRepository extends JpaRepository<Post, Long> {

    // SELECT * FROM posts WHERE title = ?
    List<Post> findByTitle(String title);

    // SELECT * FROM posts WHERE title LIKE '%keyword%'
    List<Post> findByTitleContaining(String keyword);

    // SELECT * FROM posts WHERE created_at > ?
    List<Post> findByCreatedAtAfter(LocalDateTime dateTime);

    // SELECT * FROM posts ORDER BY created_at DESC
    List<Post> findAllByOrderByCreatedAtDesc();
}
```

메서드 이름의 규칙:

- `findBy` + 필드명 → `WHERE 필드 = ?`
- `findBy` + 필드명 + `Containing` → `WHERE 필드 LIKE '%?%'`
- `findBy` + 필드명 + `After` → `WHERE 필드 > ?`
- `OrderBy` + 필드명 + `Desc` → `ORDER BY 필드 DESC`

---

## 6. H2 데이터베이스

학습 단계에서는 **H2**라는 가벼운 데이터베이스를 사용합니다.

### H2의 특징

- **인메모리 모드** — 별도 설치 없이 애플리케이션과 함께 실행됨
- **웹 콘솔 제공** — 브라우저에서 DB 내용을 직접 확인 가능
- **개발/테스트용** — 서버 종료 시 데이터가 사라짐 (설정으로 파일 저장 가능)

실무에서는 MySQL, PostgreSQL 등을 사용하지만, 학습 단계에서는 H2로 먼저 동작을 익히고 나중에 교체합니다. JPA를 사용하면 DB 교체가 매우 쉽습니다 — 설정 파일만 바꾸면 됩니다.

---

## 7. 영속성 컨텍스트 (Persistence Context)

JPA의 가장 중요한 내부 개념입니다. 지금은 깊이 들어가지 않지만, 기본 개념은 알아두어야 합니다.

### 영속성 컨텍스트란?

> **Entity 객체를 관리하는 JPA의 내부 저장소(1차 캐시)**

`entityManager.find(Post.class, 1L)`로 게시글을 조회하면:

1. 먼저 영속성 컨텍스트(1차 캐시)에서 찾음
2. 없으면 DB에 쿼리를 날려서 조회
3. 조회한 Entity를 영속성 컨텍스트에 보관
4. 같은 ID로 다시 조회하면 DB에 가지 않고 캐시에서 반환

### 변경 감지 (Dirty Checking)

영속성 컨텍스트가 관리하는 Entity의 필드를 변경하면, **별도의 save 호출 없이도** 트랜잭션이 끝날 때 자동으로 UPDATE 쿼리가 실행됩니다.

```java
Post post = postRepository.findById(1L).get();
post.setTitle("수정된 제목");  // setter만 호출
// save()를 호출하지 않아도 트랜잭션 종료 시 자동으로 UPDATE 실행!
```

이것이 가능한 이유는 영속성 컨텍스트가 Entity의 초기 상태를 스냅샷으로 저장해두고, 트랜잭션이 끝날 때 현재 상태와 비교해서 변경된 부분만 UPDATE 하기 때문입니다.

---

## 8. 트랜잭션 (Transaction)

### 트랜잭션이란?

> **여러 작업을 하나의 단위로 묶어서, 모두 성공하거나 모두 실패하게 만드는 것**

예시: 계좌 이체

1. A 계좌에서 10만원 차감
2. B 계좌에 10만원 추가

1번은 성공했는데 2번이 실패하면? A의 돈이 사라집니다.
트랜잭션으로 묶으면: 2번이 실패하면 1번도 취소됩니다 (**롤백**).

### 스프링에서의 트랜잭션

```java
@Service
public class PostService {

    @Transactional  // 이 메서드를 하나의 트랜잭션으로 처리
    public void updatePost(Long id, String title) {
        Post post = postRepository.findById(id).orElseThrow();
        post.setTitle(title);
        // 메서드가 정상 종료되면 → 커밋 (DB에 반영)
        // 예외가 발생하면 → 롤백 (변경 취소)
    }
}
```

`@Transactional`을 메서드에 붙이면:

- 메서드 시작 시 트랜잭션 시작
- 메서드 정상 종료 시 커밋 (변경사항 DB에 반영)
- 메서드에서 예외 발생 시 롤백 (변경사항 취소)

영속성 컨텍스트의 변경 감지도 트랜잭션 안에서만 동작합니다.

---

## 9. 핵심 정리

| 개념                  | 설명                                     |
|---------------------|----------------------------------------|
| **RDBMS**           | 데이터를 테이블(행/열) 형태로 저장하는 데이터베이스          |
| **SQL**             | 데이터베이스를 조작하는 언어                        |
| **ORM**             | 자바 객체와 DB 테이블을 자동 매핑하는 기술              |
| **JPA**             | 자바 ORM 표준 인터페이스. 구현체는 Hibernate        |
| **Spring Data JPA** | JPA를 더 쉽게 사용하게 해주는 스프링 모듈              |
| **Entity**          | DB 테이블과 매핑되는 자바 클래스 (`@Entity`)        |
| **Repository**      | DB 접근을 담당하는 인터페이스 (`JpaRepository` 상속) |
| **쿼리 메서드**          | 메서드 이름으로 SQL을 자동 생성하는 기능               |
| **H2**              | 설치 없이 사용 가능한 인메모리 개발용 DB               |
| **영속성 컨텍스트**        | JPA가 Entity를 관리하는 내부 1차 캐시             |
| **변경 감지**           | Entity 필드 변경 시 자동으로 UPDATE 실행          |
| **트랜잭션**            | 여러 작업을 하나로 묶어 모두 성공/모두 실패 보장           |
| **@Transactional**  | 메서드를 트랜잭션으로 처리하는 어노테이션                 |

---

## 다음 주제

이 개념들을 이해한 후, 다음으로 **연관관계 매핑 (게시글 ↔ 댓글, 1:N)**을 학습합니다.

# 연관관계 방향과 설정

## Q. 엔티티 연관관계를 설정할 때 무엇을 고려해야 하나?

**A:** 아래 체크리스트를 순서대로 판단한다.

```
1. 관계 카디널리티는? (1:N, N:1, 1:1, N:M)
2. 양방향이 정말 필요한가? (단방향으로 충분하면 단방향)
3. FK는 어느 테이블에 있는가? → 그쪽이 주인
4. @ManyToOne의 fetch를 LAZY로 바꿨는가?
5. 자식의 생명주기가 부모에 종속되는가? → cascade/orphanRemoval 결정
```

---

## Q. 연관관계 어노테이션의 종류와 파라미터는?

**A:** 관계의 주인 쪽과 반대쪽에 따라 사용하는 어노테이션과 파라미터가 다르다.

### @ManyToOne (N쪽, FK 주인)

FK를 실제로 관리하는 쪽이다.

| 파라미터 | 의미 | 기본값 | 판단 기준 |
|---------|------|-------|----------|
| `fetch` | 연관 엔티티를 언제 로딩할지 | `EAGER` | 거의 항상 `LAZY`로 변경해야 함 |

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "member_id")  // FK 컬럼명 지정
private Member member;
```

`@JoinColumn`은 생략 가능하지만, FK 컬럼명을 명시적으로 지정하는 것이 권장된다.

### @OneToMany (1쪽, 반대편)

양방향일 때만 사용하며, `mappedBy`로 읽기 전용임을 선언한다.

| 파라미터 | 의미 | 기본값 | 판단 기준 |
|---------|------|-------|----------|
| `mappedBy` | FK 주인 쪽 필드명 | 없음 (필수) | 양방향이면 반드시 지정 |
| `fetch` | 연관 엔티티를 언제 로딩할지 | `LAZY` | 기본값이 LAZY이므로 보통 그대로 둠 |
| `cascade` | 부모 작업을 자식에 전파할지 | 없음 | 생명주기 종속 여부로 판단 |
| `orphanRemoval` | 컬렉션에서 제거된 자식을 DB에서 삭제할지 | `false` | 생명주기 종속 여부로 판단 |

```java
@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Comment> comments = new ArrayList<>();
```

`mappedBy`의 값은 주인 쪽(Comment)에서 부모를 가리키는 필드 이름이다. Comment 클래스에 `private Post post`가 있으면 `mappedBy = "post"`.

---

## Q. 단방향과 양방향을 어떻게 판단하나?

**A:** "이 관계를 Repository 쿼리로 대체할 수 있는가?"를 기준으로 판단한다.

### 양방향이 불필요한 경우: Member ↔ Post

"내가 쓴 게시글 보기" 기능이 필요하더라도 양방향 관계 없이 구현 가능하다.

```java
// 방법 1: 양방향 (엔티티를 타고 감)
Member member = memberRepository.findById(memberId);
List<Post> posts = member.getPosts();  // 게시글 전체가 메모리에 로딩됨

// 방법 2: Repository 쿼리 (단방향으로 충분)
List<Post> posts = postRepository.findByMemberId(memberId, pageable);  // 필요한 만큼만
```

방법 1의 문제점:
- 회원의 게시글이 1000개면 1000개 전부 로딩
- 페이징 불가
- 실제로 필요한 건 게시글 제목과 주소 정도인데, 엔티티 전체를 가져옴

방법 2가 더 효율적이고, Member 엔티티가 `List<Post>`를 가지고 있을 필요 자체가 없다.

### 양방향이 자연스러운 경우: Post ↔ Comment

- 게시글 상세 조회 시 댓글을 함께 보여줘야 함
- 댓글 수는 보통 수십 개 수준이라 전체 로딩해도 문제없음
- `post.getComments()`로 접근하는 것이 직관적

**핵심:** 수학 공식처럼 딱 떨어지는 기준이 아니라, 상황과 필요성, 그리고 리소스를 함께 고려해야 한다.

---

## Q. FK(외래키)는 어느 테이블에 들어가나?

**A:** 1:N 관계에서 FK는 **항상 N쪽 테이블**에 들어간다. 이건 관계형 DB의 구조적 제약이다.

```
만약 members(1쪽)에 post_id를 넣으면:

| id | username | post_id |
| 1  | kim      | 10      |  ← 게시글 하나만 연결 가능
| 1  | kim      | 11      |  ← 같은 회원을 또 넣어야 함 (중복!)

posts(N쪽)에 member_id를 넣으면:

| id | title   | member_id |
| 10 | 첫 글    | 1         |
| 11 | 둘째 글  | 1         |  ← 자연스럽게 N개 표현
| 12 | 셋째 글  | 2         |
```

한 행에 FK 값이 하나만 들어가므로, 1쪽에 FK를 넣으면 N개를 표현할 수 없다. 따라서 **N쪽이 FK를 가지고, N쪽이 연관관계의 주인**이 된다.

---

## Q. cascade와 orphanRemoval은 언제 설정하나?

**A:** "부모 없이 자식이 존재할 수 있는가?"로 판단한다.

| 관계 | 부모 없이 존재 가능? | cascade | orphanRemoval |
|-----|-------------------|---------|---------------|
| Post → Comment | 댓글은 게시글 없이 의미 없음 | `ALL` | `true` |
| Member → Post | 게시글은 회원 탈퇴 후에도 남을 수 있음 | 없음 | `false` |

- `cascade = ALL`: 부모 저장 → 자식도 저장, 부모 삭제 → 자식도 삭제
- `orphanRemoval = true`: 컬렉션에서 제거된 자식을 DB에서도 삭제

자식이 부모에 완전히 종속되면 둘 다 설정, 독립적이면 둘 다 설정하지 않는다.

---

## Q. @ManyToOne의 fetch 기본값은 왜 LAZY로 바꿔야 하나?

**A:** `@ManyToOne`의 기본 fetch 전략은 `EAGER`(즉시 로딩)다. 게시글을 조회할 때마다 항상 회원 정보도 JOIN해서 가져온다. 회원 정보가 필요 없는 조회에서도 불필요한 쿼리가 발생하므로, `LAZY`로 설정해서 실제로 접근할 때만 로딩하도록 바꾸는 것이 권장된다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "member_id")
private Member member;
```

참고: `@OneToMany`는 기본값이 이미 `LAZY`이므로 별도 설정 불필요.

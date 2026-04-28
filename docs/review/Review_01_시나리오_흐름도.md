# 총정리 Part 1: 시나리오 흐름도

Phase 1~4에서 배운 개념들이 실제 요청에서 어떻게 연결되는지, 시나리오를 따라가며 직접 설명해본다.

---

## 시나리오 1: 비로그인 사용자가 게시글 목록을 조회한다

`GET /posts` 요청이 들어왔을 때, 처음부터 끝까지 어떤 일이 일어나는가?

### 질문

1. 클라이언트가 보낸 HTTP 요청이 서버에 도착하면 **가장 먼저** 어디를 거치는가?
2. 이 요청에는 토큰이 없다. Filter에서는 어떤 일이 일어나는가?
3. SecurityConfig의 인가 규칙에서 `GET /posts`는 어떻게 처리되는가?
4. Controller에 도달한 후, 어떤 메서드가 호출되는가?
5. Service에서는 어떤 일을 하는가? `@Transactional(readOnly = true)`가 붙어있는 이유는?
6. Repository에서 데이터를 가져오는 방식은? (Spring Data JPA가 하는 일)
7. DB에서 가져온 Entity를 클라이언트에 직접 반환하는가? 아니라면 왜, 어떻게 변환하는가?
8. 최종 HTTP 응답은 어떤 형태로 클라이언트에게 돌아가는가?

### 답변

(여기에 직접 작성)

1. 
클라이언트가 보낸 HTTP 요청이 제일 먼저 도착하는 곳은 톰캣 서버입니다.
톰캣 서버는 HTTP 요청이라는 데이터 조각을 받아 자바 코드가 실행될 수 있는 환경(Servlet)을 만들어주고 다시 그 결과를 HTTP 로 돌려주는 역할입니다.

톰캣 내부에서는 Connector - Engine - Host - Context - Wrapper 순으로 진행이 됩니다.

커넥터는 제일 첫번째 연결 통로로 HTTP 요청 데이터 조각을 HttpServletRequest 라는 객체로 만들어줍니다.
이를 통해 개발자는 파편화된 요청을 분석할 필요 없이 객체로 요청을 처리할 수 있습니다.

엔진은 톰캣 내부의 이름 그대로 엔진입니다.
톰캣 하나에 여러 서비스가 돌아갈 때 이를 총괄 제어하기 위한 중심점이 엔진 입니다.

호스트는 도메인 이름 별로 구역을 나눕니다.
하나의 서버 안에 여러 도메인이 존재할 수 있기 때문에 들어온 요청을 적절한 곳으로 보내주는 역할을 합니다.

컨텍스트는 사이트 내의 개별 프로그램 단위 입니다.
하나의 사이트 안에 여러 서비스가 존재할 수 있고 이를 독립적으로 관리하기 위해 존재합니다.

래퍼는 실제 요청을 처리하는 최소 단위인 서블릿을 하나씩 감싸고 있는 틀 입니다.
특절 서블릿 하나에 대한 설정, 보안, 로딩 등을 세밀하게 관리합니다.
실제 비즈니스 로직은 서블릿이 처리하지만 래퍼가 서블릿의 생명주기를 관리합니다.



2. 
GET /posts 요청은 인증, 인가를 필요로 하지 않는 요청입니다.
따라서 Filter 를 그냥 통과해 컨트롤러로 요청에 도달하게 됩니다.
구체적인 코드 동작은 다음과 같습니다.
먼저 JwtAuthenticationFilter 의 resolveToken() 이 호출되어 Authorization 헤더를 확인합니다.
토큰이 없기 때문에 null 조건에 걸려 인증이 이루어지지 않고 filterChain.doFilter() 이 호출되어 다음 필터로 넘어가게 됩니다.



3. GET /posts 는 단순 조회 작업이라 요청, 인가를 요구하지 않습니다.
이는 SecurityConfig 클래스의 filterChain 메서드를 보면 어떤 URL 에서 인증, 인가가 필요한지 확인할 수 있습니다.
따라서 인증 인가 없이 요청이 컨트롤러에 도달하며 컨트롤러에서 요청을 수행해 반환합니다.

코드를 살펴보면 다음과 같습니다.
.authorizeHttpRequests(auth -> auth
.requestMatchers("/h2-console/**").permitAll()
.requestMatchers("/auth/**").permitAll()
.requestMatchers(HttpMethod.GET, "/posts", "/posts/**").permitAll()
.requestMatchers("/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()

먼저 ("/h2-console/**") 해당 주소로 들어오는 요청은 모두 permitAll() 입니다.
인증 없이 통과가 가능합니다. ("/auth/**")도 마찬가지 입니다.
(HttpMethod.GET, "/posts", "/posts/**")는 GET HttpMethod 요청에 한해서 permitAll() 입니다.
("/admin/**") 은 hasRole, 메서드 명 그대로 해당 역할을 가졌는지 확인해서 알맞은 요청일 경우 통과됩니다.
.anyRequest().authenticated() 그 밖의 요청들은 모두 인증, 인가를 요구하도록 합니다.


4. getAllPosts() 메서드가 실행됩니다.
어떤 메서드가 실행되는지 확인하기 위해서는 Controller 클래스 전체에 걸려있는 @RequestMapping 어노테이션의 경로와
각 메서드 개별적으로 선언된 @(HttpMethod)Mapping 어노테이션에 붙은 경로를 합쳐서 확인할 수 있습니다.
GET /posts 는 posts 이후 세부 URL 이 없기 때문에 클래스 전체에 걸려있는 경로의 GET 메서드인 getAllPosts() 메서드를 실행시킵니다.

5. Service 에서는 요청에 대한 실질적 처리가 이루어집니다. Repository 클래스와 협력해 요청 처리에 필요한 값들을 조회하고
이를 알맞은 Response DTO 로 반환합니다.
@Transactional(readOnly = true) 는 JPA에게 이 작업은 단순 조회만을 수행하는 작업임을 알려 불필요한 연계 작업을
생략해 자원을 아낄 수 있도록 하는 옵션입니다.
readOnly = true 는 JPA 에게 Dirty Checking(변경 감지) 을 생략하도록 합니다.
Dirty chekcing 이란, JPA 가 엔티티의 상태 변화를 자동으로 감지해 DB 에 반영(UDPATE 쿼리)해주는 기능입니다.
JPA 는 내부적으로 스냅샷 생성 -> 비교 -> 쿼리 생성의 단계를 거칩니다.
JPA 는 엔티티를 DB 에서 처음 조회할 때 당시의 상태를 복사해서 저장해둡니다. 이것이 스냅샷 입니다.
트랜잭션이 끝나는 시점(Commit)에 현재 엔티티의 상태와 처음에 찍어둔 스냅샷을 비교합니다.
비교 결과 달라진 부분이 있다면(Dirty), JPA 가 자동으로 UPDATE SQL 을 DB에 보내줍니다.
이것이 JPA의 Dirty Checking 기능인데 readOnly 즉, 데이터 변경이 이루어지는 작업이 아닌게 확실하다면
구지 이 체크 과정을 거칠 필요가 없습니다. 따라서 이를 JPA 에게 옵션으로 미리 알려줘 dirty chekcing 과정을 생략하게 할 수 있습니다.

또 JPA 뿐 아니라 DB 쪽에서도 읽기 전용 트랜잭션으로 따로 처리되어 성능 향상을 기대할 수 있습니다.


6.
먼저 Spring Data JPA 가 하는 일 입니다.

우리는 PostRepository 라는 인터페이스만 정의하고 구현체를 작성하지 않았습니다.
그런데 postRepository.findAll() 을 호출하면 실제로 동작을 합니다. 이게 가능한 이유는 Spring Data JPA가
런타임에 구현체를 자동으로 생성해주기 때문입니다. JpaRepository 를 상속한 인터페이스를 발견하면 Spring 이 SimpleJpaRepository 라는 구현체를
자동으로 만들어 빈으로 등록합니다. findAll(), findById(), save(), deleteById() 같은 기본 CRUD 메서드가 이미 구현체에 작성되어 있습니다.
여기에 추가적으로 findByUsername(String username) 같은 메서드만 Spring Data JPA 의 메서드 명명 규칙에 맞게 선언해주면
이 또한 분석해서 알맞은 쿼리를 자동으로 생성합니다. 이걸 쿼리 메서드 기능이라고 합니다.

그 다음은 findAll() 호출 시 실제로 일어나는 일에 대해서입니다.
findAll() 은 **매번 DB에 SELECT 쿼리를 보냅니다.**
영속성 컨텍스트의 1차 캐시는 findById() 처럼 ID 기반 단건 조회에서, 같은 트랜잭션 내에서 같은 엔티티를 다시 조회할 때만 캐시가 활용됩니다.
예를 들어, 같은 트랜잭션 안에서
Post post1 = postRepository.findById(1L);
Post post2 = postRepository.findById(1L);
이렇게 코드가 실행된다면 첫번째 find 는 DB 쿼리가 실행되며 1차 캐시에 저장되고, 두번째 find 가 쿼리 없이 1차 캐시에서 바로 반환되는 식 입니다.

findAll() 은 전체 목록을 달라는 요청이기 때문에 1차 캐시만으로 해결할 수 없어 매번 SELECT * FROM post 쿼리가 실행됩니다.


7. 엔티티를 직접 반환하지 않습니다. DTO, Data Transfer Object 라는 데이터 운반용 객체를 생성해
반드시 필요한 정보만 담아 반환하게 됩니다. 이를 통해 불필요한 정보가 노출되지 않도록 보안을 강화할 수 있으며 양쪽의 결합도를 낮춰
유연한 유지보수가 가능하게 됩니다.

DTO 를 사용할 경우 방지할 수 있는 현상 중 순환 참조가 있습니다. 순환 참조란 엔티티가 서로를 참조하여 이를 직렬화 하는 과정에서 무한하게 반복되는 현상입니다.
제일 대표적인 예시가 현재 만들고 있는 게시판의 Post 와 Comment 관계 입니다.
Post 는 Comment 를 참조하고 Comment 는 Post 를 참조합니다.
post 엔티티를 그대로 반환하는 경우 직렬화 과정이 일어나면 먼저
post 를 JSON 으로 만듭니다. 이 과정에서 게시글에 포함된 comment 를 JSON 으로 만들려고 합니다.
comment 를 JSON 으로 만들려고 보니 내부에 다시 Post 가 등장합니다. 이 Post 를 JSON 으로 만드려고 시도합니다.
그러면 다시 Post 안에 comment 가 발견됩니다... 이 과정이 무한 반복되는게 순환 참조입니다.

DTO는 엔티티 전체가 아닌 딱 필요한 데이터만 담는 바구니기 때문에 이런 현상이 일어나지 않습니다.


8. JSON 형태로 돌아가게 됩니다. 이후 웹 브라우저에서 해당 정보를 파싱해 클라이언트의 화면에 적절하게 렌더링 해 보여주게 됩니다.
반환된 데이터들을 JSON 형태로 가공하는 것을 직렬화라고 하며 Jackson 라이브러리에서 이를 수행합니다. 해당 라이브러리는 Spring 에 기본적으로 내장되어 있습니다.

---

## 시나리오 2: 로그인한 사용자가 게시글을 작성한다

`POST /posts` 요청에 JWT 토큰과 게시글 데이터가 함께 들어왔을 때.

### 질문

1. 요청 헤더에 `Authorization: Bearer <token>`이 있다. Filter에서 어떤 순서로 처리되는가?
2. 토큰이 유효하면 SecurityContext에 무엇이 저장되는가? 어떤 객체에 어떤 정보가 담기는가?
3. 토큰이 만료되었다면 어떤 일이 일어나는가? 왜 GlobalExceptionHandler로 처리할 수 없는가?
4. SecurityConfig의 인가 규칙에서 `POST /posts`는 어떻게 처리되는가? (`GET /posts`와 다른 점은?)
5. Controller에서 `@AuthenticationPrincipal String username`은 어디서 오는 값인가?
6. Controller에서 `@Valid @RequestBody PostCreateRequest`는 각각 무엇을 하는가?
7. Service에서 username으로 Member를 조회하는 이유는? 어떤 Repository 메서드를 사용하는가?
8. `new Post(title, content, member)`로 생성한 Post 엔티티에서, Post와 Member의 관계는 어떤 어노테이션으로 설정되어 있는가? 왜 그렇게 설정했는가?
9. `postRepository.save(post)`를 호출하면 JPA 내부에서 어떤 일이 일어나는가? (영속성 컨텍스트)
10. 응답으로 Entity가 아닌 PostResponse(DTO)를 반환하는 이유는?
11. 응답 HTTP 상태 코드가 200이 아닌 201인 이유는?

### 답변

(여기에 직접 작성)

1. 
요청 헤더에 토큰이 포함된 경우 먼저 doFilterInternal() 메서드 안에서 먼저 resolveToken() 메서드가 작동하게 됩니다.
앞의 토큰임을 표시하는 Bearer prefix(인증 스킴)을 제거하고 토큰 값만 남겨 반환하게 됩니다.
이후 token 이 유효한지 tokenprovider 에게 검증을 요청하고 tokenprovider 는 해당 문자열이 토큰이 맞는지 파싱해서 검증 후
맞으면 true 문제가 있으면 false 를 반환하게 됩니다.
검증에 통과하면 마찬가지로 tokenprovider 에 의해 username 과 role 이 추출됩니다.
해당 데이터를 기반으로 UsernamePasswordAuthenticationToken 객체가 생성되며 이 객체를 SecurityContext 에 등록하는 코드가 실행됩니다.
이후 다음 필터를 수행하도록 지시하는 코드가 실행됩니다.

무사히 통과하면 요청은 컨트롤러에 전달되며 앞서 등록된 인가 정보를 SecurityContext 에서 조회하는걸로 로직이 실행됩니다.

2.
UsernamePasswordAuthenticationToken 객체가 생성되어 SecurityContext 에 저장됩니다.
이 객체는 Authentication 인터페이스의 구현체이며 3가지 정보를 담고 있습니다.
Principal 필드 | username | @AuthenticationPrincipal 로 꺼내 사용
Credentials 필드 | null | 인증이 완료된 상태라 비밀번호 불필요
Authorities 필드 | ROLE_USER 또는 ROLE_ADMIN | 인가 규칙에서 권한 체크에 사용


3.
토큰이 만료되면 다시 토큰을 발행 받아야합니다. 이는 토큰 방식이 서버가 아닌 클라이언트에 주도권을 주는 형식이기 때문입니다.
서버는 무상태로 클라이언트의 인증 정보를 저장하지 않기 때문에 최초의 토큰 발행 때 만료 시간을 지정해 이 시간 안에서만 해당 클라이언트의 요청이
유효하도록 처리합니다.
토큰 처리에 관한 인증, 인가 단계는 Controller 에 요청이 전달되기 전에 일어납니다.
따라서 @RestController 에서 던져지는 예외를 처리하는 @RestControllerAdvice 어노테이션의 GlobalExceptionHandler 는 요청이 컨트롤러에
도달하기 전에 이루어지는 인증, 인가 단계에서 발생하는 예외를 처리할 수 없습니다.
만약 만료된 토큰이 감지되면 validateToken() 메서드에서 ExpiredJwtException 이 발생하고 Filter 의 catch 블록에서 잡히게 됩니다.
이후 HttpServletResponse 에 직접 401 상태 코드와 에러 메시지를 기록해서 return 하고 필터 체인이 중단됩니다.


4.
/posts 의 인가 코드는 `.requestMatchers(HttpMethod.GET, "/posts", "/posts/**").permitAll()` 입니다.
/posts 최상위 루트 URL 과 이 아래 주소들을 대상으로 GET method 에 한해 permitAll, 모든 요청이 통과됩니다.
따라서 /posts URL 의 POST method 는 맨 마지막 인가 코드인 `.anyRequest().authenticated()` 의 영향을 받습니다.
인증 대상이 되기 때문에 해당 URL로 오는 POST 요청은 JWT 를 통해 인증, 인가를 받아야만 요청이 정상 수행됩니다.


5.
SecurityContext 에서 인증 정보를 가져오는 어노테이션입니다.
@AuthenticationPrincipal 어노테이션을 사용하면 컨텍스트에 저장된 Authentication 객체의 Principal 값을 꺼내옵니다.
Filter 에서 UsernamePasswordAuthenticationToken 의 첫번째 인자로 username 을 넣었기 때문에, Principal 필드에는
username 이 String 으로 담겨있고 이를 꺼내오는 어노테이션 입니다.



6.
@Valid 어노테이션은 해당 파라미터의 유효성을 검증하라는 의미입니다.
Request 등의 DTO 를 작성할때 미리 필요한 값에 대한 검증 절차를 어노테이션으로 지정해줄 수 있고 Controller 에서 @Valid 어노테이션을
지정해주면 요청이 들어왔을때 해당 DTO 를 검사해 값이 잘 들어왔으면 통과, 문제가 있으면 예외가 발생합니다.

@RequestBody 어노테이션은 클라이언트의 요청 JSON 을 역직렬화 해 객체로 변환하라는 의미입니다.
톰캣에서 HttpRequestServlet 에 클라이언트의 요청인 JSON 본문이 담깁니다. 서블릿이 무사히 컨트롤러까지 도착하면
여기서 JSON 을 꺼내 Jackson 라이브러리를 이용해 요청을 지정된 객체로 생성하게 됩니다.

7.
Service 에서 username 으로 Member(엔티티) 를 조회하는 이유는 Securitycontext 에서 꺼낼 수 있는 값이 username 이기 때문입니다.
클라이언트가 보내온 요청에서 존재하는 값은 Member 의 ID 가 아닌 username 이기 때문에 Filter 에서 SecurityContext 에 저장한
Principal 도 username 밖에 없으며 따라서 @AuthenticationPrincipal 로 꺼낼 수 있는 값도 username 뿐입니다.

Post 엔티티를 생성하려면(POST /posts -> createPost()) Member 엔티티 객체가 필요하기 때문에 memberRepository 에서
.findByUsername() 메서드를 통해 Member 엔티티를 불러오고 이를 가지고 새로운 Post 를 작성합니다.


`new Post(title, content, member)`로 생성한 Post 엔티티에서, Post와 Member의 관계는 
어떤 어노테이션으로 설정되어 있는가? 왜 그렇게 설정했는가?
8.
post 클래스에서 @ManyToOne 으로 설정되어 있습니다.
이는 한 member 가 여러개의 post 를 쓸 수 있으며 여러 post 가 하나의 똑같은 member를 가질 수 있기 때문입니다.
post와 comment 의 경우 서로가 서로를 참조하고 @OneToMany 와 @ManyToOne 으로 설정되어 있지만
post 와 member 는 member 쪽에서 post 를 참조하지 않습니다.
게시글을 조회할 대 댓글을 함께 보는 것은 자연스럽고, 댓글에서 소속 게시글을 아는 것도 자연스럽기 때문에 양방향이 맞지만,
반면 멤버를 조회할 때 그 멤버의 모든 게시글을 불러오는 것은 불필요합니다. 단방향의 관계로 하는 것이 자연스럽습니다.

9.
save() 메서드는 새로 생성된 엔티티를 저장하는 메서드 입니다. 기존 엔티티 변경을 감지하는 Dirty Checking 과는 다른 작업입니다.

1)save() 메서드 호출 시, JPA 는 이 엔티티가 새 엔티티인지 기존 엔티티인지 판단합니다. (ID 가 null 일 경우 새 엔티티 임을 알 수 있습니다.)
2)새 엔티티임이 판별되면 EntityManger.persist() 가 호출됩니다.
3)이후 엔티티가 영속성 컨텍스트에 등록됩니다. 1차 캐시에 저장되며 스냅샷이 생성됩니다.
4)트랜잭션이 커밋 되는 시점에 INSERT SQL 이 DB 로 전송됩니다.


10.
DTO 를 반환하는 이유는 엔티티를 그대로 반환하게 되면 순환 참조의 위험성이 있고 클라이언트에게 불필요한 필드를 노출시키는 보안 문제가 있으며
UI와 애플리케이션 간의 결합도가 높아지는 문제가 있기 때문입니다.

11.
200 OK 는 요청이 성공적으로 처리됨을 알리는 상태 코드로 범용적으로 사용됩니다.
201 Created 는 요청의 결과로 새로운 리소스가 생성됨을 알리는 역할입니다.

따라서 200 을 써도 작동에는 문제가 없지만 좀 더 명시적으로 요청에 대해 뭔가가 새로 만들어졌음을 클라이언트에게 명확하게 알리고자
201 코드를 사용합니다.


---

## 시나리오 3: 다른 사용자의 게시글을 수정하려 한다

유저 B가 로그인한 상태에서 유저 A가 작성한 게시글에 `PUT /posts/1` 요청을 보낼 때.

### 질문

1. 유저 B의 토큰은 유효하다. Filter와 인가 규칙을 무사히 통과하는가?
2. Controller에 도달했다. 어떤 파라미터들이 메서드에 전달되는가?
3. Service에서 게시글을 조회한 후, 작성자 검증은 어떻게 이루어지는가? 무엇과 무엇을 비교하는가?
4. 검증 실패 시 어떤 예외가 던져지는가? 이 예외는 어디서 잡히는가?
5. 최종적으로 클라이언트에게 어떤 HTTP 상태 코드와 메시지가 반환되는가?
6. 만약 유저 A 본인이 수정 요청을 보냈다면, 수정은 어떻게 이루어지는가? (`postRepository.save()`를 호출하는가?)
7. 6번에서 save를 호출하지 않아도 된다면, 그 이유는 무엇인가?

### 답변


(여기에 직접 작성)
유저 B가 로그인한 상태에서 유저 A가 작성한 게시글에 `PUT /posts/1` 요청을 보낼 때. (`updatePost()` 메서드)

1.
Filter 와 인가 규칙 자체는 통과됩니다.
왜냐하면 토큰의 유효성 검사는 getClaims() 메서드에서 서버에서 지정한 키만을 가지고 유효성을 검사하기 때문입니다.
다른 조건이 없기 때문에 요청은 통과되어 SecurityContext 인증,인가 내용이 등록됩니다.


2.
`PUT /posts/1` URL 요청은 수정하고자 하는 id 와 수정 요청에 관한 리퀘스트, 그리고 요청자의 인증, 인가 정보(Principal) 세 가지 입니다.
하지만 이후 해당 컨트롤러의 메서드가 실행되면서 username 과 수정하고자 하는 게시글의 저자가 같은지 검사하게되는데 여기서 예외가 발생합니다.

파라미터에 전달되는 값들은 다음과 같습니다.
@PathVariable 는 URL 요청에 담긴 값을 변수로 받습니다.
@Valid @RequestBody 사용자의 요청을 알맞은 DTO 객체로 생성합니다. 이 때 유효성 검사도 같이 합니다.
@AuthenticationPrincipal SecurityContext 에 등록된 인가 정보 중 Principal 필드의 값을 가져옵니다.

3.
   `if (!username.equals(post.getAuthor())) {
   throw new UnauthorizedAccessException("게시글을 수정할 권한이 없습니다");
   }`
해당 코드 부분으로 인증된 username 과 게시글의 getAuthor() 메서드가 일치하는지 검사해 불일치 시 예외를 던집니다.
getAuthor 메서드는 post 가 가지고 있는 Member 클래스의 useranme 을 반환합니다.




4. 
3번의 코드에서 보이듯, UnauthorizedAccessException 이 던져집니다. 권한이 없을때 던져지는 커스텀 예외이며
이 예외가 던져지면 GlobalExceptionHandler 에서 이를 잡고 알맞은 처리를 합니다.



5.
`PUT /posts/1` 요청을 유저 B 가 보내는 시나리오로 이는 요청 처리를 실패하는 시나리오 입니다.
예외가 발생하며 GlobalExceptionHandler 에서 이를 잡아
`return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);` 코드가 실행됩니다.
ResponseEntity 에 FORBIDDEN, 403 에러 코드와 error 객체를 실어 반환하게 됩니다.
에러메시지는 3번의 코드에서 보이는 에러 메시지가 담깁니다.


6.
먼저 A 가 요청을 보내 updatePost() 메서드가 실행된다면 `postRepository.findById(id)` 메서드가 호출된다.
이 과정에서 엔티티는 반드시 영속성 컨텍스트에 캐싱된다.
이후 인가 검사가 통과되면 게시글의 변경사항이 set 되며 Response DTO 가 작성되어 반환된다.
이 때 JPA 는 트랜잭션 커밋 시점이 오면 현재 엔티티의 상태와 스냅샷을 비교한다.(Dirty Checking)
달라진 부분을 발견하게 되고 자동으로 UPDATE SQL 쿼리가 나간다.
따라서, save() 메서드 호출 없이 변경사항이 자동으로 DB 에 반영된다.


7. 6번에서 save를 호출하지 않아도 된다면, 그 이유는 무엇인가?
JPA 가 자동으로 트랜잭션이 끝나고 커밋될 때 영속성 컨텍스트와 비교합니다.
여기서 달라진 부분을 발견하게 되면 save() 메서드 호출 없이도 자동으로 UPDATE SQL 쿼리가 나가게 됩니다.

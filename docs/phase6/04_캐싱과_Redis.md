# 캐싱과 Redis

## 캐싱이란

자주 조회되는 데이터를 빠른 저장소에 복사해두고 재사용하는 기법. DB 접근 횟수를 줄여 성능을 향상시킨다.

```
CPU 캐시:     디스크 → RAM → CPU 캐시 (자주 쓰는 데이터를 가까운 곳에)
웹 캐싱:      DB → Redis (자주 조회하는 데이터를 빠른 저장소에)
```

## Spring Cache

### @Cacheable — 캐시 저장

```java
@Cacheable("posts")
public PostResponse getPost(Long id) { ... }
```

- 첫 번째 호출: 캐시에 없음 → DB 조회 → 결과를 캐시에 저장 → 반환
- 두 번째 호출: 캐시에 있음 → DB를 거치지 않고 캐시에서 바로 반환

`"posts"`는 캐시 이름(저장소 구분), 파라미터(`id`)가 자동으로 key가 된다.

### @CacheEvict — 캐시 제거

```java
@CacheEvict(value = "posts", key = "#id")
public PostResponse updatePost(Long id, PostUpdateRequest request, String username) { ... }
```

데이터가 수정/삭제될 때 해당 캐시를 즉시 제거한다. 다음 조회 시 DB에서 새 데이터를 가져와 다시 캐싱한다.

- `value` — 어떤 캐시 저장소에서 제거할지 (@Cacheable의 이름과 일치)
- `key = "#id"` — SpEL(Spring Expression Language)로 메서드 파라미터 참조. 파라미터가 여러 개일 때 어떤 것을 key로 쓸지 명시

파라미터가 하나뿐이면 key 생략 가능 (@Cacheable). 여러 개면 명시 필요 (@CacheEvict).

### @EnableCaching

메인 애플리케이션 클래스에 추가하여 Spring Cache를 활성화한다.

### TTL (Time To Live)

캐시 데이터의 자동 만료 시간. 수동 제거(@CacheEvict)와 함께 사용하여 이중 안전장치를 둔다.

## 왜 Redis인가

### Spring 기본 캐시의 한계

Spring 기본 캐시(ConcurrentMapCache)는 애플리케이션 메모리(JVM 힙)에 저장된다.

- 서버 재시작 시 캐시 소멸 → 배포 직후 모든 요청이 DB로 몰림 (캐시 스탬피드)
- 서버 여러 대일 때 각자 독립적 캐시 → 데이터 불일치 발생

```
1번 서버: getPost(1L) → 캐시에 "원래 제목" 저장
2번 서버: updatePost(1L, "새 제목") → 2번 서버 캐시에서만 제거
1번 서버: getPost(1L) → 1번 서버 캐시에 "원래 제목" 남아있음 → 옛날 데이터 반환
```

### Redis 사용 시

```
기본 캐시:  [서버1 캐시]  [서버2 캐시]  ← 각자 따로
Redis:      [서버1] → [Redis] ← [서버2]  ← 공유 캐시
```

- 서버 재시작해도 Redis에 데이터가 남아있음
- 서버 여러 대여도 같은 Redis를 바라보니 일관성 유지
- TTL 관리를 Redis가 담당

## Redis 기본 개념

**Redis = Remote Dictionary Server**

메모리(RAM) 기반의 키-밸류 저장소. 디스크 기반 DB보다 수십~수백 배 빠르다.

### 데이터 구조

기본은 키-밸류이지만, 밸류에 여러 자료구조를 담을 수 있다:

```
String:     "post:1" → "{title: '제목', content: '내용'}"   ← 캐시에 사용
List:       "recent_posts" → [post3, post2, post1]
Set:        "post:1:likers" → {userA, userB, userC}
Hash:       "user:1" → {name: "kim", age: "25"}
Sorted Set: "ranking" → [(score:100, userA), (score:95, userB)]
```

### 캐시로서의 저장 형태

```
키:   "posts::1"
밸류: JSON 문자열 (@class 타입 정보 포함)
TTL:  1800초 (30분 후 자동 삭제)
```

### Redis의 위치 (인프라 관점)

```
개발 환경: [Spring Boot] → [Redis (Docker, 로컬)] → [H2 DB (내장)]
운영 환경: [Spring Boot 서버들] → [Redis 서버 (별도)] → [MySQL 서버 (별도)]
```

Redis는 MySQL처럼 독립적인 서버 프로세스. 개발 시 로컬 Docker로 실행하고, 운영 시 외부 서버(AWS ElastiCache 등)로 연결 주소만 변경.

### 메모리인데 재시작하면?

RDB(주기적 스냅샷), AOF(쓰기 로그) 옵션이 있지만, 캐시 용도로는 날아가도 무방. 원본은 DB에 있으므로.

## Docker로 Redis 실행

```bash
# Redis 컨테이너 실행
docker run --name board-redis -p 6379:6379 -d redis

# 상태 확인
docker ps

# Redis CLI 접속
docker exec -it board-redis redis-cli

# 주요 명령어
KEYS *              # 전체 키 조회
GET posts::1        # 특정 키의 값 조회
TTL posts::1        # 남은 TTL(초) 확인
FLUSHALL            # 전체 캐시 삭제
```

### Docker를 쓰는 이유

- 환경 일치: 모든 개발자가 동일한 버전/설정으로 실행
- 간편한 설치/제거: `docker run` → 실행, `docker rm` → 깔끔하게 제거
- 여러 서비스 관리: Redis, MySQL, Kafka 등을 한 번에 관리

## Spring Boot + Redis 연동

### 1. 의존성 추가

```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

### 2. application.properties

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.cache.type=redis
spring.cache.redis.time-to-live=1800000
```

### 3. CacheConfig (직렬화 설정)

Redis는 외부 저장소이므로 Java 객체를 JSON으로 변환(직렬화)해서 저장해야 한다.

```java
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        SerializationPair.fromSerializer(serializer)
                )
                .entryTtl(Duration.ofMinutes(30));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
```

주요 설정:

- `JavaTimeModule` — LocalDateTime 등 Java 8 날짜 타입 직렬화 지원
- `WRITE_DATES_AS_TIMESTAMPS` 비활성화 — 날짜를 배열이 아닌 ISO 문자열로 저장
- `activateDefaultTyping` — JSON에 `@class` 필드를 추가하여 역직렬화 시 올바른 클래스로 변환

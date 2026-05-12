# 스레드 풀 vs 커넥션 풀

이름이 비슷해 헷갈리기 쉽다. 둘 다 **풀링(pooling)** 패턴을 따르지만, **재사용하는 자원의 종류**가 다르다.

## 1. 공통점 — 풀링 패턴

자원을 매번 새로 만들고 버리는 대신, **미리 일정 수만큼 만들어두고 빌려 쓰고 반납**하는 패턴.

- 자원 생성/소멸 비용을 줄임
- 자원 수에 상한을 두어 무한정 늘어나는 것을 방지

## 2. 차이점

| 항목                | 스레드 풀                                                | 커넥션 풀                                  |
|-------------------|-----------------------------------------------------|----------------------------------------|
| 재사용 자원            | OS 스레드                                              | DB 연결 (TCP + 인증된 세션)                   |
| 비용의 원인            | OS 시스템 콜 (스레드 생성/소멸), 컨텍스트 스위칭                     | TCP 핸드셰이크 + 인증 + 세션 초기화                |
| 대표 구현             | `ThreadPoolTaskExecutor` (Spring), `ExecutorService` (Java) | HikariCP, Apache DBCP                  |
| 주 용도              | `@Async`, `@Scheduled`, 톰캣 요청 처리                    | JDBC/JPA에서 DB 연결 빌려오기                  |
| Spring Boot 기본    | 톰캣 풀(요청 처리), `SimpleAsyncTaskExecutor`(`@Async` 기본) | HikariCP                               |

### 커넥션 풀의 비용 — 왜 비싼가

DB 연결 한 번 만들 때마다:
1. TCP 3-way handshake
2. DB 인증 (사용자/비밀번호 검증)
3. 세션 변수 / 트랜잭션 격리 수준 등 초기화

연결 한 번에 수십 ms ~ 수백 ms가 들 수 있다. 매 쿼리마다 새로 만들면 비즈니스 로직 시간보다 연결 비용이 더 클 수도 있다.

### 스레드 풀의 비용 — 왜 비싼가

스레드 생성은 OS 시스템 콜이다. 커널이 PCB(Process/Thread Control Block), 스택 영역(보통 1MB), 스케줄링 큐 등록 등을 수행한다. 또한 너무 많은 스레드가 동시에 살아 있으면 **컨텍스트 스위칭 비용**이 폭증해 오히려 처리량이 떨어진다.

## 3. 풀이 가득 찼을 때의 동작이 다르다

### 커넥션 풀 — 기본은 "대기"

사용 가능한 커넥션이 없으면 `connection-timeout` 동안 대기하고, 그래도 안 나오면 예외.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      connection-timeout: 30000   # 30초 대기 후 실패
```

DB 쿼리는 반드시 처리되어야 하는 본질적 작업이라 "대기"가 합리적 기본값이다.

### 스레드 풀 — 명시적 "거부 정책"

스레드도 다 차고, 큐도 가득 찼을 때 새 작업이 들어오면 — 어떻게 처리할지를 **거부 정책(`RejectedExecutionHandler`)** 으로 선택한다.

| 정책                    | 동작                                  |
|-----------------------|-------------------------------------|
| `AbortPolicy` (기본)    | 예외(`RejectedExecutionException`) 발생 |
| `CallerRunsPolicy`    | 호출자 스레드가 직접 실행 (역압 효과)             |
| `DiscardPolicy`       | 조용히 폐기                              |
| `DiscardOldestPolicy` | 큐의 가장 오래된 작업 폐기 후 새 작업 추가          |

비동기 작업은 종류가 다양해서 "유실해도 되는 작업"과 "반드시 처리할 작업"이 섞이므로 명시 선택이 필요하다.

## 4. Spring에서의 실제 모습

### 커넥션 풀 (HikariCP)

Spring Boot에 기본 내장. `application.yml`만 건드리면 된다.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
```

JPA가 트랜잭션을 시작하면 풀에서 커넥션을 빌리고, 트랜잭션 종료 시 반납한다.

### 스레드 풀 (ThreadPoolTaskExecutor)

`@Async`나 `@Scheduled`에 사용. 직접 빈으로 등록한다.

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");
        executor.initialize();
        return executor;
    }
}
```

또한 톰캣도 별도의 스레드 풀(웹 요청 처리용)을 가지고 있다.

```yaml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
```

→ 한 애플리케이션에 보통 **여러 개의 스레드 풀**이 존재한다. 톰캣 풀, `@Async`용 풀, `@Scheduled`용 풀 등.

## 5. 풀 크기는 무한대가 아니라 튜닝 대상

두 풀 모두 **풀 크기가 곧 시스템의 동시성 한계**다.

- 너무 작으면 → 대기/거부가 발생, 처리량 낮음
- 너무 크면 → 컨텍스트 스위칭/DB 부하 증가로 오히려 성능 저하

운영 환경에서는 부하 테스트로 결정하는 튜닝 대상이다. "기본값 그대로" 사용은 보통 좋은 선택이 아니다.

## 6. 정리

- 풀링 패턴은 같지만 재사용 대상이 다르다 — **스레드 풀: OS 스레드 / 커넥션 풀: DB 연결**
- 가득 찼을 때의 기본 동작이 다르다 — **커넥션 풀: 대기 / 스레드 풀: 거부 정책 발동**
- Spring Boot 기본 — 커넥션 풀은 HikariCP가 알아서 설정됨. `@Async` 스레드 풀은 명시 설정 권장
- 두 풀 모두 크기가 동시성 한계이므로 부하 특성에 맞춰 튜닝

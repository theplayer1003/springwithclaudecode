# Spring Batch 입문 — Tasklet

## 1. 배치 처리란

### 시나리오

게시판이 운영된 지 2년이 지나면 게시글 테이블에 *수십만 ~ 수억 건* 의 데이터가 쌓인다. 이 중 대부분은 *오래되어 더 이상 활발히 사용되지 않는* 데이터. 이런 데이터를 다음과 같은 작업으로 처리하고 싶다:

- *1년 이상 된 게시글을 아카이빙 테이블로 이동*
- *매일 자정에 통계 집계*
- *비활성 회원을 일괄 비활성화 처리*
- *대량 정산 정리*

이런 작업의 공통점:

> **대량 데이터를 정기적으로 일관된 방식으로 처리한다.**

이걸 **배치 처리 (Batch Processing)** 라고 부른다.

### 배치 처리의 특징

- **대량**: 한 건이 아닌 수천 ~ 수억 건을 다룸
- **정기성**: *매일 새벽 3시*, *매주 일요일* 같은 *시간 트리거*
- **비동기**: 사용자 요청과 무관하게 *백그라운드* 에서 실행
- **격리**: 활성 서비스 트래픽에 영향 최소화 (보통 *오프피크 시간대*)

### 왜 배치가 필요한가

대안과 비교하면 본질이 드러난다:

| 방식 | 한계 |
|--|--|
| **API 한 번에 처리** | 대량 처리는 응답 시간 초과 (timeout). 사용자 요청 흐름에 적합하지 않음. |
| **수동 SQL 실행** | 재실행, 실패 추적, 진행 상태 관리가 어려움. *사람의 작업* 으로는 정기성 보장 안 됨. |
| **단순 스케줄러 + 메서드** | 처리 단위, 트랜잭션 관리, 재시작 같은 *공통 패턴* 을 매번 직접 구현해야 함. |

→ Spring Batch 는 *배치 처리의 공통 패턴을 추상화* 한 프레임워크. *재시작 가능성*, *진행 상태 추적*, *청크 단위 트랜잭션*, *통계 수집* 같은 기능을 제공한다.

---

## 2. Spring Batch 의 핵심 구성요소

### 추상화 계층

```
[Job]            ← 배치 작업의 최상위 단위 (예: "오래된 게시글 아카이빙 Job")
  └─ [Step]      ← Job 안의 처리 단계 (예: "아카이빙 단계")
       └─ [Tasklet 또는 Reader/Processor/Writer]   ← Step 안의 실제 작업
```

### 각 개념의 의미

**Job** — *하나의 배치 작업 전체*. 비즈니스 단위. 예: "매일 새벽 3시에 실행되는 오래된 게시글 아카이빙".

**Step** — *Job 안의 처리 단계*. 한 Job 안에 여러 Step 이 있을 수 있다. 예: Job A 가 *Step 1 (데이터 정리)* → *Step 2 (통계 집계)* → *Step 3 (이메일 알림)* 으로 구성.

**Tasklet** — *Step 의 가장 단순한 형태*. *한 번의 실행 단위*. 예: *"디렉토리 정리"*, *"로그 회전"*.

> *Chunk-oriented Step* 은 Step 의 또 다른 형태로, *Reader/Processor/Writer* 패턴으로 구성된다. 자세한 내용은 다음 문서.

### 인프라 구성요소

Spring Batch 가 *내부적으로 사용하는* 컴포넌트들:

**JobLauncher** — Job 을 *실제로 실행* 시키는 역할. `JobLauncher.run(job, parameters)` 로 호출.

**JobRepository** — 배치 실행의 *메타데이터를 저장* 하는 저장소. *어떤 Job 이 언제 실행되었는지*, *어떤 Step 이 어디까지 진행되었는지* 등을 DB 메타 테이블에 기록.

**JobParameters** — Job 실행 시 *외부에서 주입* 하는 파라미터. *같은 Job 인스턴스를 여러 번 실행* 할 수 있도록 *각 실행을 구분* 하는 데 사용. 보통 *실행 시각* 같은 값을 넣음.

```java
JobParameters params = new JobParametersBuilder()
    .addString("requestDate", LocalDateTime.now().toString())
    .toJobParameters();
jobLauncher.run(job, params);
```

> **왜 JobParameters 가 필요한가**: Spring Batch 는 *같은 JobParameters 로 같은 Job 을 두 번 실행하지 못하게* 막는다 (`JobInstanceAlreadyCompleteException`). *재시도* 의 의미를 명확히 하기 위함. 매 실행마다 *시각* 같은 *유일한 값* 을 넣어 *다른 실행임을 명시*.

---

## 3. Tasklet — 가장 단순한 Step

### Tasklet 인터페이스

```java
@FunctionalInterface
public interface Tasklet {
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception;
}
```

- *함수형 인터페이스* — 람다로 구현 가능
- `execute()` 가 *한 번의 작업* 을 수행
- `RepeatStatus.FINISHED` 를 반환하면 *Step 종료*, `RepeatStatus.CONTINUABLE` 이면 *반복 호출*

### 가장 단순한 예 — Hello Tasklet

```java
@Bean
public Tasklet helloTasklet() {
    return (contribution, chunkContext) -> {
        System.out.println("Hello, Spring Batch");
        return RepeatStatus.FINISHED;
    };
}
```

### Step 으로 감싸기

```java
@Bean
public Step helloStep(JobRepository jobRepository, Tasklet tasklet,
                       PlatformTransactionManager transactionManager) {
    return new StepBuilder("stepOne", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
}
```

- `StepBuilder` 로 Step 생성. 이름 부여 (`"stepOne"`).
- `.tasklet(tasklet, transactionManager)` 으로 *Tasklet 기반 Step 임을 명시*.
- `transactionManager` 는 *Step 실행 시 트랜잭션 관리* 용.

### Job 으로 감싸기

```java
@Bean
public Job helloJob(JobRepository jobRepository, Step helloStep) {
    return new JobBuilder("jobOne", jobRepository)
            .start(helloStep)
            .build();
}
```

- `JobBuilder` 로 Job 생성.
- `.start(helloStep)` 으로 *시작 Step* 지정.
- 여러 Step 을 연결하려면 `.next(secondStep)`, `.next(thirdStep)` ... 형식.

---

## 4. 스케줄링 — 정기적 실행

배치 처리의 핵심 특성 중 하나가 *정기성*. Spring 의 `@Scheduled` 어노테이션과 결합해 트리거 설정:

```java
@Component
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job helloJob;
    
    @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
    public void runHelloJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addString("requestDate", LocalDateTime.now().toString())
            .toJobParameters();
        jobLauncher.run(helloJob, params);
    }
}
```

### `@EnableScheduling` 활성화

`@Scheduled` 가 동작하려면 *어딘가에* `@EnableScheduling` 어노테이션이 있어야 한다. 보통 `@Configuration` 클래스 또는 메인 애플리케이션 클래스에 부착.

### Cron 표현식 (Spring)

Spring 의 cron 은 **6필드**:

```
초   분   시   일   월   요일
```

흔한 표현:
- `0 0 3 * * *` — 매일 새벽 3시 정각
- `0 */5 * * * *` — 매 5분마다 (0초 시점)
- `0 0 0 1 * *` — 매월 1일 자정
- `0 0 0 * * 0` — 매주 일요일 자정

⚠️ **초 필드를 `*` 로 두면 *매 초마다 실행* 됩니다**. 폭주의 원인. 자세한 내용은 `QA_Cron_표현식_함정.md` 참조.

---

## 5. 메타 통찰

### Spring Batch 의 설계 철학

> **"배치 처리의 공통 패턴을 추상화하고, 비즈니스 로직만 작성하게 만든다."**

학습자가 작성한 *Hello Tasklet* 코드를 보면 *비즈니스 로직 (한 줄의 println)* 외에는 *프레임워크가 제공* 하는 구조 (Job, Step, JobLauncher, JobRepository, JobParameters) 가 *대부분*. 이게 *프레임워크의 본질* — *공통의 구조를 가져가고, 비즈니스만 채우게 함*.

### 왜 Job > Step > Tasklet 의 계층인가

이 계층 분리가 *복잡한 배치 시나리오* 에서 빛난다:

- 한 *Job* 안에 *여러 Step* — *순차 실행*, *조건부 분기*, *병렬 실행* 가능
- 각 *Step* 의 *단위 작업* 을 Tasklet 또는 Chunk-oriented 로 자유 선택
- *재시작 시* 어떤 Step 부터 다시 시작할지 메타데이터 기반 추적

이번 Phase 8 은 *기본 구조* 만 다뤘지만, *Phase 10 의 아키텍처 심화* 단계에서는 *복잡한 Job 흐름 (조건부 Step, 병렬 Step)* 을 다룰 수 있다.

### 다음 단계

Tasklet 은 *단순한 작업* 에 적합하다. 그러나 *대량 데이터를 처리* 하려면 *한 번에 다 메모리에 올리면 OOM*. 그래서 **Chunk-oriented Step** 이라는 패턴이 등장한다. 다음 문서.

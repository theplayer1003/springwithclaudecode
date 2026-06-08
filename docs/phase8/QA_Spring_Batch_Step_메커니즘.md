# Spring Batch Step 의 내부 메커니즘 — Reader 의 버퍼링, ItemStream, 재시작

## 질문 배경

Phase 8 의 학습 중 떠오른 의문들:

1. *Reader/Processor/Writer 가 호출되는 방식은?*
2. *Reader 가 500개를 가져온 뒤 한 개씩 어떻게 처리되는가?*
3. *Step 객체가 셋을 어떻게 묶어 호출하는가?*
4. *재시작이 정밀하게 작동하려면 무엇이 필요한가?*

본 문서는 Spring Batch 의 Chunk-oriented Step 의 *내부 동작* 을 정리한다.

---

## 1. Chunk-oriented Step 의 의사코드

전체 흐름:

```java
// 대략의 흐름 (실제 코드를 단순화)
public void executeStep() {
    while (true) {
        beginTransaction();
        
        // 1. Reader 를 chunkSize 만큼 호출 → 청크 채우기
        List<I> items = new ArrayList<>();
        for (int i = 0; i < chunkSize; i++) {
            I item = reader.read();
            if (item == null) break;  // Reader 가 null = 데이터 끝
            items.add(item);
        }
        
        if (items.isEmpty()) {
            commitTransaction();
            break;  // Step 종료
        }
        
        // 2. Processor 를 각 item 마다 호출
        List<O> processed = new ArrayList<>();
        for (I item : items) {
            O result = processor.process(item);
            if (result != null) processed.add(result);
        }
        
        // 3. Writer 를 한 번 호출 (청크 전체)
        writer.write(new Chunk<>(processed));
        
        commitTransaction();
    }
}
```

### 핵심 관찰

- **Reader 는 한 건씩 N번** (chunkSize 회) 호출
- **Processor 는 각 item 마다 1번씩** (chunkSize 회) 호출
- **Writer 는 청크당 1번만** 호출 (배치 처리)

Reader/Processor 와 Writer 의 *호출 빈도가 다른* 비대칭이 핵심.

---

## 2. Reader 가 *한 개씩 노출* 하는 메커니즘

### 단순한 의문

학습자의 NoOffSetItemReader 는 *DB 에서 한 번에 500개를 가져옴*. 그런데 Spring Batch 는 *한 개씩* 받아 처리. **이 변환이 어디서 일어나는가?**

### 답 — Reader 안의 버퍼링

학습자가 직접 작성한 코드 안에 메커니즘이 있음:

```java
public Post read() throws Exception {
    if (!currentPageIterator.hasNext()) {
        fetchNextPage();  // 500개를 한 번에 가져오기 (DB 통신)
    }
    if (!currentPageIterator.hasNext()) {
        return null;  // 정말 끝
    }
    Post nextPost = currentPageIterator.next();  // 버퍼에서 한 개 꺼냄
    return nextPost;  // 한 개 반환
}
```

### 흐름

1. Spring Batch 가 `read()` 호출 → *버퍼가 비어있음* → *DB 에서 500개 가져옴* → 1번째 반환
2. Spring Batch 가 `read()` 호출 → *버퍼에 499개 남음* → 2번째 반환
3. ... (반복) ...
4. Spring Batch 가 `read()` 500번째 호출 → 500번째 반환
5. Spring Batch 가 `read()` 501번째 호출 → *버퍼 비어있음* → *DB 에서 다음 500개 가져옴* → 501번째 반환

### 핵심

> **DB 통신은 500번에 한 번, 하지만 Reader 외부로는 한 개씩 노출.**

이게 **버퍼링 (Buffering) 패턴**. *I/O 비용을 묶음* 으로 처리하면서 *흐름은 한 건씩 유지*.

이건 *Spring Batch 만의 패턴이 아니라* 일반적 I/O 최적화 패턴. *BufferedReader*, *BufferedWriter*, *DB 커서 fetch size* 등이 모두 같은 결.

---

## 3. Spring Batch 의 내부 클래스 구조

Step 의 *추상* 뒤에 있는 *실제 구현 클래스* 들:

### TaskletStep

```
[Step (인터페이스)]
       ↓
[TaskletStep (실제 구현)]
       └─ Tasklet 을 실행하는 Step
```

**중요**: Chunk-oriented Step 도 *내부적으로는 TaskletStep*. *Reader/Processor/Writer 가 ChunkOrientedTasklet 으로 감싸짐*.

### ChunkOrientedTasklet

```java
public class ChunkOrientedTasklet<I> implements Tasklet {
    private final ChunkProvider<I> chunkProvider;       // Reader 호출 + chunk 채우기
    private final ChunkProcessor<I> chunkProcessor;     // Processor + Writer
    
    public RepeatStatus execute(...) {
        Chunk<I> inputs = chunkProvider.provide(...);   // 청크 채우기
        chunkProcessor.process(..., inputs);             // Processor → Writer
        return RepeatStatus.CONTINUABLE;
    }
}
```

### 3중 구조

```
[TaskletStep]
       └─ [ChunkOrientedTasklet]
                ├─ [ChunkProvider]  ← Reader 를 chunkSize 만큼 호출
                └─ [ChunkProcessor] ← Processor 호출 + Writer 호출
```

각 단계가 *책임을 분리*. 관심사의 분리 (Separation of Concerns).

---

## 4. Step = Tasklet 통일

학습자가 *Hello Tasklet* 시점에 직접 작성한 `Tasklet`:

```java
@Bean
public Tasklet helloTasklet() {
    return (contribution, chunkContext) -> {
        System.out.println("Hello, Spring Batch");
        return RepeatStatus.FINISHED;
    };
}
```

이 *Tasklet 인터페이스* 가 **Chunk-oriented Step 의 ChunkOrientedTasklet** 과 같은 추상.

### 통일된 그림

```
모든 Step = Tasklet 을 실행

[Tasklet 기반 Step]
  → 학습자가 직접 만든 Tasklet 실행

[Chunk-oriented Step]
  → Spring 이 ChunkOrientedTasklet 으로 자동 감싸서 실행
```

→ *Chunk-oriented Step 은 사실 "편의 추상"* — Spring 이 *Reader/Processor/Writer 를 ChunkOrientedTasklet 으로 자동 묶어줌*.

### 메타 통찰

> **상위 개념으로 통일, 하위에서 다양화.**
> *Tasklet 이 더 본질적 추상*, *Chunk-oriented 는 그 위의 편의 레이어*. 이런 *통일된 추상 + 편의 레이어* 가 *프레임워크의 본질*.

---

## 5. RepositoryItemReader 의 page 카운터

학습자가 사용했던 (그 후 교체한) `RepositoryItemReader` 의 내부 구조:

### AbstractPagingItemReader

```java
public abstract class AbstractPagingItemReader<T> {
    private int pageSize = 10;
    private volatile int current = 0;      // 현재 페이지 안의 위치
    private volatile int page = 0;          // 페이지 번호
    private volatile List<T> results;       // 현재 페이지의 데이터

    public T read() throws Exception {
        if (results == null || current >= pageSize) {
            doReadPage();           // 다음 페이지 가져오기
            page++;                  // 페이지 카운터 증가
            current = 0;
        }
        if (current < results.size()) {
            return results.get(current++);
        }
        return null;
    }
    
    protected abstract void doReadPage();  // 자식이 구현
}
```

### RepositoryItemReader.doReadPage()

```java
@Override
protected void doReadPage() {
    Pageable pageRequest = PageRequest.of(page, pageSize, sort);  // page 사용
    Page<T> result = (Page<T>) method.invoke(repository, /* args + */ pageRequest);
    results = new ArrayList<>(result.getContent());
}
```

### 핵심

- `page` 가 *Reader 의 내부 상태*. *0 부터 시작, 한 번 가져올 때마다 증가*.
- `PageRequest.of(page, pageSize)` 가 *LIMIT pageSize OFFSET pageSize*page* 로 변환됨.
- **`page` 가 OFFSET 의 정체**.

학습자의 `NoOffSetItemReader` 는 *이 `page` 카운터를 제거* 하고 *`lastSeenId` 로 대체* 한 것.

자세한 내용은 `QA_OFFSET_페이지_시프트_Keyset.md` 참조.

---

## 6. ItemStream + ExecutionContext — *재시작 메커니즘*

### 재시작이란

Spring Batch 의 강력한 기능 — *Job 이 중간에 실패해서 종료된 후, 같은 JobInstance 로 다시 실행하면 *마지막으로 성공한 청크 다음부터* 이어서 진행*.

```
[1차 실행]
  청크 1 (id 1~1000) ✅ COMMIT
  청크 2 (id 1001~2000) ✅ COMMIT
  청크 3 (id 2001~3000) ❌ 실패 → ROLLBACK
  → Job 실패 종료

[같은 JobInstance 로 재실행]
  청크 1 건너뜀 (이미 성공)
  청크 2 건너뜀 (이미 성공)
  청크 3 부터 재시작 ← id 2001 부터
```

→ *처음부터 다시* 가 아닌 *이어서*.

### 가능하게 하는 메커니즘 — ExecutionContext

Spring Batch 가 *각 Step 의 진행 상태* 를 **DB 메타 테이블** 에 저장:
- *현재 page 카운터*
- *마지막으로 본 id*
- *처리한 아이템 수*
- ... 사용자 정의 값

이걸 저장/복원하는 표준 인터페이스가 **`ItemStream`**:

```java
public interface ItemStream {
    void open(ExecutionContext executionContext);    // 시작 시 — 저장된 상태 복원
    void update(ExecutionContext executionContext);  // 청크 커밋 직전 — 현재 상태 저장
    void close();                                    // 끝날 때
}
```

### 동작 흐름

1. **Step 시작 시 `open()` 호출** — 메타 테이블에 *이전 실행의 상태* 가 있으면 복원
2. **각 청크 커밋 직전 `update()` 호출** — 현재 상태를 메타 테이블에 저장
3. **Step 끝 시 `close()` 호출** — 자원 정리

### 청크 단위 박제

핵심: **`update()` 가 청크 커밋 시점에 호출**. 즉 *청크 단위로 진행 상태가 저장*.

```
[청크 N 처리 중]
  Reader.read() x M번 (chunkSize 만큼)
  Processor.process() x M번
  Writer.write() 1번
  → ItemStream.update() ← 현재 진행 상태를 ExecutionContext 에 저장
  → COMMIT
```

만약 *이 청크 안에서 실패* → 트랜잭션 롤백 → *ExecutionContext 도 그 청크 이전 상태로 유지* → 재시작 시 *이 청크부터 다시*.

---

## 7. 학습자 코드의 한계 — *재시작 미지원*

학습자가 만든 `NoOffSetItemReader` 는 **`ItemStream` 을 구현하지 않음**. `ItemReader` 만 구현.

```java
public class NoOffSetItemReader implements ItemReader<Post> {
    // ItemStream 미구현
}
```

따라서:
- *상태 저장/복원 안 함*
- *재시작 시 lastSeenId 가 0 으로 초기화* → *처음부터 다시*

### 정석 — ItemStream 까지 구현

재시작을 지원하려면:

```java
public class NoOffSetItemReader implements ItemReader<Post>, ItemStream {
    
    private Long lastSeenId = 0L;
    private Iterator<Post> currentPageIterator = Collections.emptyIterator();
    
    @Override
    public void update(ExecutionContext ctx) {
        ctx.putLong("lastSeenId", this.lastSeenId);  // 청크 커밋 시점에 저장
    }
    
    @Override
    public void open(ExecutionContext ctx) {
        if (ctx.containsKey("lastSeenId")) {
            this.lastSeenId = ctx.getLong("lastSeenId");  // 재시작 시 복원
        }
    }
    
    @Override
    public void close() {
        // 자원 정리 (필요 시)
    }
    
    @Override
    public Post read() throws Exception { /* 기존 로직 */ }
}
```

### 학습 단계의 선택

이번 학습은 *재시작까지 구현하지 않음*. 이유:
- *학습 우선순위는 페이지 시프트 함정의 해결*
- *재시작은 별도 학습 영역*
- *학습 시나리오에서는 재시작이 필요 없음* (한 번 실행 후 끝)

> **재시작이 필요 없으면 *처음부터 다시* 가 디폴트.** 학습자 시나리오가 이쪽.
> **재시작이 필요하면 *ItemStream 까지 구현* 해야 한다.** Phase 10 의 아키텍처 심화에서 다룰 가치.

---

## 8. JobLauncher 와 JobRepository — *재시작의 기반*

ItemStream 의 *상태 저장 메커니즘* 이 동작하는 *기반* 이 무엇인가?

### JobRepository

**Spring Batch 의 메타데이터 저장소**. *Job 실행의 모든 진행 상황* 을 DB 에 기록.

자동 생성되는 메타 테이블:
- `BATCH_JOB_INSTANCE` — Job 의 *논리적 인스턴스*
- `BATCH_JOB_EXECUTION` — Job 의 *실행 시도*
- `BATCH_STEP_EXECUTION` — Step 의 *실행 상태*
- `BATCH_STEP_EXECUTION_CONTEXT` — *Step 의 ExecutionContext*
- `BATCH_JOB_EXECUTION_PARAMS` — JobParameters

### JobInstance vs JobExecution

이 둘의 차이가 *재시작* 의 핵심:

- **JobInstance**: *논리적 실행 단위*. *동일한 JobParameters* 면 같은 JobInstance.
- **JobExecution**: *실제 실행 시도*. 한 JobInstance 가 여러 번 실행될 수 있음 (재시작).

```
[같은 JobParameters로 두 번 실행]
   JobInstance: 1개
   JobExecution: 2개 (첫 시도, 재시도)
   
[다른 JobParameters로 실행]
   JobInstance: 2개 (별도 인스턴스)
```

### JobParameters 의 의미

학습자의 BatchScheduler 가 매 실행마다 *다른 requestDate* 를 JobParameters 로 넣는 이유:

```java
JobParameters params = new JobParametersBuilder()
    .addString("requestDate", LocalDateTime.now().toString())
    .toJobParameters();
```

→ *매 실행이 새 JobInstance*. 같은 인스턴스의 *재시도가 아닌 새 실행*. 재시작 메커니즘은 활용 안 됨.

만약 *재시작이 의도* 라면 *같은 JobParameters* 로 두 번 호출해야 함. 그러면 Spring Batch 가 *이전 실행 상태를 ExecutionContext 에서 복원* 해서 *이어서 진행*.

---

## 9. lastSeenId 갱신 시점의 재시작 영향

학습자의 read 단위 갱신:

```java
Post nextPost = currentPageIterator.next();
this.lastSeenId = nextPost.getId();   // ← read() 마다 갱신
return nextPost;
```

만약 ItemStream 까지 구현된 상황에서:

- `update()` 가 *청크 커밋 시점* 에 호출되어 *현재 lastSeenId 를 ExecutionContext 에 저장*
- 청크 안에서 실패 시 *그 청크 이전 시점의 lastSeenId* 가 유지됨
- 재시작 시 *그 lastSeenId 부터 다시 진행*

학습자 패턴 (read 단위 갱신) 과 페이지 단위 갱신 패턴의 차이:

- **청크 안에서 페이지 경계가 어긋난 경우** (예: chunk 1000, page 500 이 아닌 chunk 700, page 500):
  - 청크 안에서 *페이지 1개 + 일부* 를 읽는 일이 발생
  - read 단위 갱신: *마지막으로 읽은 아이템 id* 가 정확히 lastSeenId
  - 페이지 단위 갱신: *마지막으로 읽은 페이지의 끝* 이 lastSeenId (실제 마지막 아이템보다 앞일 수 있음)

→ *재시작 시 read 단위 갱신이 더 정확*.

### 학습자 시나리오에서는

chunk 1000, page 500 → *청크 안에 정확히 2 페이지*. 두 패턴이 *같은 결과*. 차이가 안 나타남.

> **두 패턴의 차이는 *청크 사이즈와 페이지 사이즈가 배수 관계가 아닐 때* + *ItemStream 까지 구현* 했을 때만 의미 있는 미세한 차이.**

---

## 10. 종합 — 데이터의 흐름 한눈에

```
[Scheduler] cron 매칭
    ↓
[JobLauncher.run(job, params)]
    ↓
[JobRepository] JobInstance/JobExecution 생성
    ↓
[Job] Step 실행
    ↓
[TaskletStep] = ChunkOrientedTasklet 실행
    ↓
loop:
    BEGIN TRANSACTION
    [ChunkProvider]
        ↓
        [Reader.read()] 한 건씩 chunkSize 번
        (Reader 내부: 버퍼링 + 페이지 단위 DB 통신)
        ↓
    [ChunkProcessor]
        ↓
        [Processor.process()] 한 건씩 N번
        [Writer.write()] 한 번 (청크 전체)
        ↓
    [ItemStream.update()] (구현되어 있다면)
        ExecutionContext 에 현재 상태 저장
        ↓
    COMMIT TRANSACTION
end loop (Reader 가 null 반환 시)
    ↓
[Step 완료]
    ↓
[Job 완료]
```

---

## 11. 메타 통찰

### 추상화의 단계적 분해

Spring Batch 의 설계 철학을 분해:

```
배치 처리라는 문제
    ↓
한 번에 못 하니까 단위로 쪼개자 (chunk)
    ↓
chunk 안에서도 *읽기 / 변환 / 쓰기* 가 분리되어야 한다
    ↓
Reader / Processor / Writer 패턴
    ↓
재시작이 필요한 경우 → 상태 박제 메커니즘 필요
    ↓
ItemStream + ExecutionContext
    ↓
JobRepository 로 메타데이터 영속화
```

각 추상화 레이어가 *직전 레이어의 한계* 를 풀어내는 *점진적 정교화*. *프레임워크 설계의 모범*.

### 상태 박제 = 견고한 시스템의 기초

> **재시작이 정밀하게 작동하려면 *어디까지 진행되었는가* 가 메타 저장소에 박제되어야 한다.**

이건 *Spring Batch 만의 이야기가 아님*:

- **Kafka 의 consumer offset 커밋**: 처리된 메시지의 *offset 을 broker 에 박제*
- **분산 트랜잭션의 Two-Phase Commit**: 각 단계의 *상태를 트랜잭션 로그에 박제*
- **체크포인트 패턴**: 장시간 작업의 *진행 상태를 주기적으로 저장*

> *진행 상태의 박제* 는 *견고한 시스템의 공통 패턴*. 한 번 익히면 여러 영역에 적용 가능.

### *지금은 학습용, 다음은 정석으로*

이번 학습은 *재시작 미지원* 으로 단순화. 그러나 *재시작 본질* 을 이해해두었으므로 *향후 필요할 때 ItemStream 까지 확장* 가능.

> *완전한 구현 = 학습용 단순 구현 + 점진적 확장*. 처음부터 모든 걸 다 하려 하지 말고, *핵심 학습 → 확장 여지 이해 → 필요 시 확장* 의 흐름.

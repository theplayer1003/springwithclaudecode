# Chunk-oriented Step — Reader / Processor / Writer

## 1. 문제 — *대량 데이터를 한 번에 처리할 수 없다*

### 시나리오

1억 건의 게시글 중 *제목을 대문자로 변환해 새 컬럼에 저장* 하고 싶다. Tasklet 으로 구현하면:

```java
@Bean
public Tasklet bulkUppercaseTasklet(PostRepository postRepository) {
    return (contribution, chunkContext) -> {
        List<Post> all = postRepository.findAll();  // ← 1억 건 메모리에 적재
        for (Post post : all) {
            post.setTitle(post.getTitle().toUpperCase());
            postRepository.save(post);
        }
        return RepeatStatus.FINISHED;
    };
}
```

이 코드의 *치명적 문제* 들:

### 1.1. OutOfMemoryError

`findAll()` 이 1억 건의 Post 엔티티를 *한 번에 JVM 메모리에 적재*. 엔티티 하나가 평균 2KB 면 200GB. *JVM 힙으로는 불가능*.

### 1.2. 트랜잭션 폭증

1억 건의 `save()` 가 *모두 한 트랜잭션 안* 에 묶여 있다. *트랜잭션 로그*, *Undo 영역*, *Lock* 이 폭발적으로 증가. DB 가 마비.

### 1.3. 부분 실패의 비용

처리 도중 *9999만 번째에서 에러* 가 나면? *9999만 건의 작업이 모두 롤백*. *처음부터 다시*. 재실행도 같은 문제 반복.

### 본질

> **대량 데이터의 처리는 *흘려보내며 (streaming)* 해야 한다. 한 번에 다 잡으면 안 된다.**

이걸 위한 패턴이 **Chunk-oriented Step**.

---

## 2. Chunk-oriented Step 의 구조

### Reader → Processor → Writer

```
[Reader]      ← 데이터를 한 건씩 읽어옴
   ↓
[Processor]   ← 한 건씩 변환/가공
   ↓
[Writer]      ← chunk 단위로 모아서 한 번에 출력
```

각 컴포넌트의 책임:

| 컴포넌트 | 책임 | 인터페이스 |
|--|--|--|
| **Reader** | 데이터 소스에서 한 건씩 가져오기 | `ItemReader<I>` |
| **Processor** | 한 건을 변환 (Optional) | `ItemProcessor<I, O>` |
| **Writer** | chunk 단위로 모아 출력 | `ItemWriter<O>` |

### 인터페이스 살펴보기

```java
public interface ItemReader<T> {
    @Nullable
    T read() throws Exception;  // 한 건 반환. 더 없으면 null.
}

public interface ItemProcessor<I, O> {
    @Nullable
    O process(@NonNull I item) throws Exception;  // 한 건 변환. null 반환 시 필터링.
}

public interface ItemWriter<T> {
    void write(@NonNull Chunk<? extends T> chunk) throws Exception;  // chunk 단위로 출력.
}
```

→ Reader/Processor 는 *한 건씩*, Writer 는 *chunk 단위*. 이 비대칭성이 핵심.

---

## 3. Chunk 트랜잭션 — *원자성의 단위*

### chunk 의 의미

**chunk size** = *한 트랜잭션에 처리할 아이템 개수*. Step 정의 시 명시:

```java
.<Post, Post>chunk(1000, transactionManager)
```

→ 1000 개를 모아 *한 번에* Writer 호출, *한 트랜잭션* 으로 처리.

### 실제 동작 흐름

```
[청크 N 시작]
   BEGIN TRANSACTION
       Reader.read() → 1번째 아이템
       Reader.read() → 2번째 아이템
       ...
       Reader.read() → 1000번째 아이템    ← chunk size 만큼 반복
       
       Processor.process(item1) → 결과1
       Processor.process(item2) → 결과2
       ...
       Processor.process(item1000) → 결과1000   ← 각각 변환
       
       Writer.write([결과1, 결과2, ..., 결과1000])   ← 한 번에 출력
   COMMIT TRANSACTION
[청크 N 끝]
```

### chunk 트랜잭션의 의미

**원자성 보장** — 청크 안의 *모든 작업이 함께 성공하거나 함께 롤백*. 부분 처리 상태가 남지 않음.

```
처리 도중 999번째에서 에러 → 청크 전체 롤백
→ 1~999 처리는 *되돌려짐*
→ 그 청크는 *완전히 처음부터* 다시 처리 가능
```

### *왜 chunk 단위인가*

- *너무 작은 단위 (예: 1건)*: 트랜잭션 오버헤드 큼. *DB 왕복 횟수* 증가.
- *너무 큰 단위 (예: 100만 건)*: 메모리 압박. 부분 실패 시 비용 큼.
- **chunk = 적당한 중간**. 보통 *100 ~ 1000* 이 디폴트.

이게 *대량 처리의 균형점*: *작은 트랜잭션 여러 번 vs 큰 트랜잭션 한 번* 의 중간.

---

## 4. Step 의 의사코드

```java
// 대략의 흐름 (실제 코드를 단순화)
public void executeStep() {
    while (true) {
        beginTransaction();
        
        // 1. Reader 를 chunkSize 만큼 호출 → 청크 채우기
        List<I> items = new ArrayList<>();
        for (int i = 0; i < chunkSize; i++) {
            I item = reader.read();
            if (item == null) break;
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

### Reader 가 *null* 을 반환하면

Spring Batch 는 *데이터의 끝* 으로 인식하고 *Step 을 종료*. 따라서 Reader 구현 시 *더 이상 가져올 게 없으면 반드시 null 반환* 해야 한다.

### Processor 가 *null* 을 반환하면

해당 아이템을 *필터링* (= 결과에서 제외). Writer 로 넘어가지 않음. *조건부 처리* 에 유용.

---

## 5. RepositoryItemReader — JPA 기반 Reader

Spring Batch 가 제공하는 *기성 Reader* 중 하나. **JPA Repository 의 메서드를 호출** 해 데이터를 가져옴.

### 사용 예

```java
@Bean
public RepositoryItemReader<Post> postReader(PostRepository postRepository) {
    return new RepositoryItemReaderBuilder<Post>()
        .name("postReader")
        .repository(postRepository)
        .methodName("findAll")
        .pageSize(500)
        .sorts(Collections.singletonMap("id", Direction.ASC))
        .build();
}
```

### 동작 메커니즘

- 내부에 *page 카운터* 보유 (0, 1, 2, ...)
- `read()` 호출 시 *현재 페이지가 비어 있으면* DB 에서 `LIMIT pageSize OFFSET pageSize*page` 로 가져옴
- 가져온 페이지를 *한 건씩* 반환
- 한 페이지 다 소진하면 *다음 페이지* (`page++`)

### 핵심 빌더 메서드

- `.name(...)` — ExecutionContext 키. *유니크* 해야 함
- `.repository(...)` — JPA Repository 빈
- `.methodName(...)` — Repository 의 메서드 이름
- `.arguments(...)` — 메서드의 *Pageable 외 파라미터*. 위치 순서대로
- `.pageSize(...)` — *DB I/O 한 번에 가져올 양*
- `.sorts(...)` — ORDER BY 절. **필수에 가까움** (페이징의 일관성 보장)

> **`.sorts(...)` 가 필수에 가까운 이유**: `LIMIT + OFFSET` 은 *결과 집합의 위치 기반*. ORDER BY 가 없으면 *DB 가 반환 순서를 보장하지 않음* → *페이지 사이 중복/누락* 가능. 자세한 내용은 `QA_Spring_Data_JPA_메커니즘.md` 참조.

---

## 6. ItemProcessor — 변환과 필터링

### 단순 변환

```java
@Bean
public ItemProcessor<Post, String> uppercaseProcessor() {
    return post -> post.getTitle().toUpperCase();
}
```

`Post → String` 변환. 입력 타입과 출력 타입이 다를 수 있음 (제네릭 `<I, O>`).

### 함수형 인터페이스

`ItemProcessor` 는 *함수형 인터페이스 (SAM)*. 람다로 짧게 구현 가능. *익명 클래스* 와 동등.

### Stateless 원칙

> **Processor 는 상태를 가지면 안 된다.** *한 건을 받아 한 건을 변환* 하는 *순수 함수* 처럼 동작.

이유: Spring Batch 의 *재시도/병렬 처리/재시작* 메커니즘이 *Processor 가 부수효과 없다* 는 전제로 동작.

만약 Processor 가 *외부 상태에 의존* 한다면:
- 재시도 시 *상태 누적 중복*
- 병렬 시 *race condition*
- 재시작 시 *복원 불가*

### null 반환 = 필터링

```java
return post -> {
    if (post.getTitle().isEmpty()) {
        return null;  // 빈 제목은 처리하지 않음
    }
    return post.getTitle().toUpperCase();
};
```

→ 결과에서 *제외*. Writer 로 안 넘어감.

---

## 7. ItemWriter — chunk 단위 출력

### 단순 출력

```java
@Bean
public ItemWriter<String> consoleWriter() {
    return chunk -> {
        for (String item : chunk) {
            System.out.println(item);
        }
    };
}
```

`Chunk<? extends T>` 를 받음 — *여러 아이템을 한 번에*.

### DB 저장 (벌크)

```java
@Bean
public ItemWriter<Post> postWriter(PostRepository postRepository) {
    return chunk -> {
        List<? extends Post> items = chunk.getItems();
        postRepository.saveAll(items);  // 벌크 저장
    };
}
```

`saveAll` 사용 → 단건 `save` 반복보다 효율적. (`hibernate.jdbc.batch_size` 설정과 함께 사용 시 *JDBC 배치 INSERT* 효과.)

### Chunk 의 제네릭 — `<? extends T>`

`ItemWriter<T>` 의 `write` 메서드 시그니처:

```java
void write(Chunk<? extends T> chunk);
```

`? extends T` 는 *공변성을 위한 와일드카드*. *T 또는 T 의 하위 타입의 chunk 도 받을 수 있게* 함. 자세한 내용은 `QA_제네릭_와일드카드_PECS.md` 참조.

---

## 8. Step 으로 묶기

```java
@Bean
public Step uppercaseStep(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager,
                            RepositoryItemReader<Post> reader,
                            ItemProcessor<Post, String> processor,
                            ItemWriter<String> writer) {
    return new StepBuilder("uppercaseStep", jobRepository)
            .<Post, String>chunk(10, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
}
```

### 주목할 부분

- **`.<Post, String>chunk(10, transactionManager)`** — 제네릭에 *Reader 가 읽어오는 타입* 과 *Processor 가 내보내는 타입* 명시
- `.reader/processor/writer` 의 순서 — Spring Batch 의 *Reader → Processor → Writer 흐름* 을 그대로 반영
- chunk size 가 첫 인자

---

## 9. Page Size vs Chunk Size — 두 개념의 분리

자주 헷갈리는 두 개념:

| | **Page Size** | **Chunk Size** |
|--|--|--|
| 책임 | DB I/O 한 번에 가져올 양 | 트랜잭션 + 처리 단위 |
| 영향 | DB 부하, 네트워크 | 애플리케이션 메모리, 트랜잭션 로그 |
| 구현 위치 | Reader 의 `.pageSize(...)` | Step 의 `.chunk(N, ...)` |

### 디폴트 — *일치시키기*

대부분의 경우 **`pageSize = chunkSize` 가 가장 단순하고 효율적**. 분리는 *튜닝 시점* 의 도구.

만약 `chunk=100` 인데 *DB 부하가 부담된다면* `page=20` 으로 *5번 나눠* 가져옴. 처리는 한 번에 100건씩, DB I/O 는 작게 끊어서.

---

## 10. 메타 통찰

### 대량 처리의 원칙

> **한 번에 다 잡지 말고, 흘려보내며 처리하라.**

이게 *Chunk-oriented Step* 의 본질. *스트리밍 사고방식*. *데이터베이스*, *파일*, *메시지 큐* 어디서든 같은 원칙.

### 추상화의 단계적 분해

```
대량 처리라는 문제
    ↓
한 번에 못 하니까 단위로 쪼개자 (chunk)
    ↓
chunk 안에서도 *읽기 / 변환 / 쓰기* 가 분리되어야 한다
    ↓
Reader / Processor / Writer 패턴
```

이 분해의 가치 — *각 단계가 독립적으로 교체 가능*. *데이터 소스를 DB 에서 파일로* 바꾸면 *Reader 만 교체*. *변환 로직만* 바꾸면 *Processor 만 교체*. **관심사의 분리 (Separation of Concerns)** 의 교과서적 예시.

### 다음 단계

지금까지 *학습용 단순 시나리오* (대문자 변환) 였다. 다음은 *실전 배치* — *오래된 게시글 아카이빙*. *INSERT + DELETE 가 함께 일어나는 시나리오*, *데이터 변화 중의 페이징 함정*, *대량 처리의 미묘한 문제들* 을 다룬다.

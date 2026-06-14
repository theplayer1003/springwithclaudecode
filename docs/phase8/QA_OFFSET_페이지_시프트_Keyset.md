# OFFSET 페이지 시프트 함정과 Keyset Pagination

## 질문 배경

Phase 8 의 아카이빙 배치 첫 실행 후 발견된 *치명적 문제*:

> 옛 데이터 1500건 중 *1000건만 아카이빙되고 500건이 누락*.

원인 추적 결과 **OFFSET 기반 페이징의 본질적 함정** 이었다. 이 함정은 *실무에서 발견하면 데이터 손실로 이어질 수 있는 결정적 문제*. 본 문서에서 메커니즘과 해법을 정리한다.

---

## 1. 문제 시나리오 (실제 발생한 일)

### 설정

- chunk 1000, pageSize 500
- 옛 데이터 (cutoff 이전): id 1~1500 (1500건)
- 신 데이터: id 1501~3000

### 청크 1 진행

```
Reader: page 0 (OFFSET 0) → id 1~500 가져옴
Reader: page 1 (OFFSET 500) → id 501~1000 가져옴
chunk 가득 참 (1000건)
Processor 1000번 호출
Writer: saveAll + deleteAllByIdInBatch
COMMIT

[이 시점 DB 상태]
  POST 의 옛 데이터: id 1001~1500 (500건) 만 남음
  신 데이터: 1501~3000 그대로
```

### 청크 2 시도

```
Reader: page 2 (OFFSET 1000)
  → cutoff 이전 데이터는 현재 500건뿐 (id 1001~1500)
  → OFFSET 1000 으로 조회 → 0건 반환
Reader: null 반환 → Step 종료

[결과]
  ARCHIVED_POST: 1000건 (의도: 1500건)
  POST: 옛 500건 + 신 1500건 = 2000건
  → id 1001~1500 (500건) 건너뜀!
```

---

## 2. 함정의 본질 — *위치 기반 페이징의 한계*

### OFFSET 의 의미

```sql
SELECT * FROM post WHERE created_at < ? ORDER BY id LIMIT 500 OFFSET 1000;
```

→ *"현재 결과 집합에서 1000번째 위치부터 500개 가져와라"*.

**중요**: OFFSET 은 *id 의 값이 1000보다 큰 것* 이 아니라 *현재 결과 집합의 위치 1000번째*.

### 학습자가 처음 가졌던 오해

> *"무의식 중에 LIMIT OFFSET 처리가 id 기반으로 이루어질거라고 생각"*

→ **틀린 직관**. OFFSET 은 *id 와 무관*. *결과 집합의 절대 위치*.

### id 값 ≠ 위치

평소엔 *id 값* 과 *정렬 위치* 가 일치 (id 1, 2, 3, ...). 하지만 *데이터가 사라지면* 어긋남:

```
DELETE 전: id [1, 2, 3, 4, 5]   ← id 값 = 위치
DELETE 후: id [3, 4, 5]          ← id 3 의 위치는 1번째, id 4 의 위치는 2번째
```

### 진짜 본질

> **OFFSET 은 *변하는 결과 집합 위의 위치*. 결과 집합이 변하면 OFFSET 의 의미도 변한다.**

학습자 시나리오:
- *읽으면서 동시에 같은 데이터를 DELETE*
- → 결과 집합이 *축소됨*
- → *Reader 의 page 카운터* 와 *실제 데이터의 위치* 어긋남

---

## 3. 왜 Reader 의 page 카운터가 *계속 진행* 되는가

`RepositoryItemReader` 의 내부 구조:

```java
public abstract class AbstractPagingItemReader<T> {
    private volatile int page = 0;   // ← OFFSET 의 정체
    
    public T read() {
        if (results == null || current >= pageSize) {
            doReadPage();  // 다음 페이지 가져오기
            page++;         // ← 페이지 카운터 증가
        }
        // ...
    }
    
    protected void doReadPage() {
        Pageable pageRequest = PageRequest.of(page, pageSize, sort);
        // ... LIMIT pageSize OFFSET pageSize*page 로 변환됨
    }
}
```

- `page` 가 *Reader 의 내부 상태*. 한 번 읽으면 *증가*, 줄어들 일 없음.
- 첫 페이지 (`page=0`, OFFSET 0) → 다음 (`page=1`, OFFSET 500) → 다음 (`page=2`, OFFSET 1000) → ...
- *Chunk 와 무관하게 Reader 가 자기 페이스로 진행*.

### Reader 의 stateful 특성

> **Reader 가 *내부 상태 (page 카운터)* 를 들고 *계속 앞으로* 만 간다.** 데이터가 사라져도 *카운터는 뒤로 가지 않음* — 그래서 함정 발생.

---

## 4. 일반화 — *읽으면서 쓰는 모든 시나리오*

이 함정은 *Spring Batch 의 문제가 아닌* — *변하는 데이터 위에서 OFFSET 페이징을 쓰는 모든 시나리오* 의 본질적 문제.

### 흔히 발견되는 예

- **대량 마이그레이션**: 옛 테이블 → 새 테이블로 이동하며 옛 삭제
- **정리 배치**: 조건 만족 데이터를 처리하며 표시/삭제
- **페이징된 무한 스크롤**: 서버 측에서 데이터 변경 시 *클라이언트가 같은 데이터를 두 번 받거나 일부를 못 받음*
- **검색 결과 페이지**: 다른 사용자가 데이터를 추가/삭제하면서 페이지 사이에 *중복/누락*

### 핵심 원칙

> **페이징은 *변하지 않는 스냅샷 위에서만* 신뢰할 수 있다. 변하는 데이터 위에서 OFFSET 페이징을 쓰면 어긋난다.**

---

## 5. 해법 — Keyset Pagination

### Keyset 의 본질

> **OFFSET 의 *위치 기반* 대신, *변하지 않는 값 (id)* 기반으로 진행한다.**

```sql
-- OFFSET 방식 (변하는 위치)
WHERE created_at < :cutoff ORDER BY id LIMIT 500 OFFSET 500

-- Keyset 방식 (변하지 않는 값)
WHERE created_at < :cutoff AND id > :lastSeenId ORDER BY id LIMIT 500
```

### 왜 작동하는가

`id > :lastSeenId` 조건은 *데이터가 사라져도 의미가 변하지 않음*:

- `lastSeenId = 1000` 이면 *"id 가 1000보다 큰 것 중 처음 500개"*
- DB 에서 *id 1~1000 이 삭제되어도* *id 1001 부터 시작하는 결과* 가 정확히 반환됨

→ **데이터 변화에 면역**.

### Keyset 의 추가 이점

OFFSET 의 또 다른 약점 — *깊은 페이지의 비효율* — 도 해결:

```
OFFSET 1000000 → DB 가 *100만 개를 읽고 그 자리를 건너뛴 다음, 다음 N개를 반환*
                 (반환은 N개지만 내부 작업은 100만 건)

WHERE id > :lastSeenId → 인덱스 활용 → 항상 일정한 성능
```

→ Keyset 은 *페이지 깊이와 무관한 일정한 성능*. 대량 데이터 처리의 정석.

---

## 6. 커스텀 Reader 구현 — NoOffSetItemReader

Spring Batch 의 기본 Reader 중 Keyset 을 지원하는 게 없어서 `ItemReader` 인터페이스 직접 구현:

```java
public class NoOffSetItemReader implements ItemReader<Post> {

    private final PostRepository postRepository;
    private final LocalDateTime cutoff;
    private final int pageSize;

    private Long lastSeenId = 0L;
    private Iterator<Post> currentPageIterator = Collections.emptyIterator();

    public NoOffSetItemReader(PostRepository postRepository, LocalDateTime cutoff, int pageSize) {
        this.postRepository = postRepository;
        this.cutoff = cutoff;
        this.pageSize = pageSize;
    }

    @Nullable
    @Override
    public Post read() throws Exception {
        if (!currentPageIterator.hasNext()) {
            fetchNextPage();
        }
        if (!currentPageIterator.hasNext()) {
            return null;  // 더 가져올 게 없음 → Reader 종료
        }
        Post nextPost = currentPageIterator.next();
        this.lastSeenId = nextPost.getId();
        return nextPost;
    }

    private void fetchNextPage() {
        PageRequest pageRequest = PageRequest.of(0, pageSize);  // ← page 0 고정
        List<Post> posts = postRepository.findByCreatedAtBeforeAndIdGreaterThanOrderByIdAsc(
            cutoff, lastSeenId, pageRequest);
        if (!posts.isEmpty()) {
            this.currentPageIterator = posts.iterator();
        } else {
            this.currentPageIterator = Collections.emptyIterator();
        }
    }
}
```

### 핵심 변화

- `page` 카운터가 *사라짐*. 대신 `lastSeenId` 가 *상태*.
- `PageRequest.of(0, pageSize)` — *항상 page 0*. OFFSET 사용 안 함.
- 페이지의 *마지막 id 보다 큰 것* 을 조건으로 다음 페이지 진행.

### Repository 메서드

```java
List<Post> findByCreatedAtBeforeAndIdGreaterThanOrderByIdAsc(
    LocalDateTime cutoff, Long lastSeenId, Pageable pageable
);
```

- 메서드 이름 쿼리로 자동 생성됨
- `Page<Post>` 가 아닌 `List<Post>` — Keyset 은 *총 개수가 의미 없음* (페이지 개념 사라짐)

### Reader 의 버퍼링 패턴

- *DB 통신은 페이지 단위로 묶음* (500개를 한 번에)
- *외부 (Spring Batch) 에는 한 개씩 노출* (Iterator 의 `next()`)
- → *DB 왕복 줄이면서 흐름은 한 건씩*

### Iterator 패턴의 선택

`ItemReader.read()` 는 *외부에서 한 번씩 호출* 받는 구조. *호출 사이에 상태 유지* 가 필요한데:

- `Iterator` 는 *List + 위치 추적* 을 *한 객체로 캡슐화* → 자연스러움
- `for-i` 로 구현하면 *List 와 인덱스 i 를 별도 필드로 보유* → 둘을 *수동으로 동기화* 해야 함

→ `Iterator` 가 *외부 호출 패턴* 에 가장 적합.

---

## 7. lastSeenId 갱신 시점의 두 가지 패턴

### 패턴 A — read() 단위 갱신 (학습자 선택)

```java
Post nextPost = currentPageIterator.next();
this.lastSeenId = nextPost.getId();  // ← 한 개 꺼낼 때마다 갱신
return nextPost;
```

### 패턴 B — 페이지 단위 갱신

```java
List<Post> nextPage = ...;
lastSeenId = nextPage.get(nextPage.size() - 1).getId();  // 페이지 가져올 때만
currentPageIterator = nextPage.iterator();
```

### 두 패턴의 차이

| | 패턴 A (read 단위) | 패턴 B (페이지 단위) |
|--|--|--|
| 갱신 빈도 | 매 `read()` 호출마다 | 페이지당 1번 |
| 관점 | *진행 상황 항상 최신화* | *페이지 = 묶음*, 끝에서 한 번 |
| 중단 시 | *마지막으로 읽은 위치* 정확 | *마지막 페이지의 끝* 만 기억 |

### 어느 게 더 나은가

대부분의 경우 *둘 다 정확하게 동작*. 차이는 *재시작 시점* 의 정밀도:

- *ItemStream + ExecutionContext* 까지 구현해서 *재시작을 정확히 지원* 하는 경우 → 패턴 A 가 미세하게 더 정확
- *재시작 미지원* 의 경우 (이번 학습) → 두 패턴 동일 결과

학습자 시나리오에서는 *패턴 A 가 의미 있는 정밀함*. 다만 *비용은 무시할 수준*.

---

## 8. 다른 해결책의 스펙트럼

학습자가 선택한 Keyset 외에도 여러 해법:

### 옵션 A — `JpaCursorItemReader`

DB 커서 기반 스트리밍. 페이징이 아니라 *결과 집합을 한 번 열고 한 줄씩 흘려보내기*.

- 장점: 코드 변경 최소 (Reader 만 교체). OFFSET 자체가 사라짐.
- 단점: 한 트랜잭션 안에서 *DB 커서 유지* → 대량 데이터 장시간 처리 시 *DB 커넥션 점유 길어짐*.

### 옵션 B — 읽기와 삭제를 별도 Step 으로 분리

- Step 1: 옛 데이터 → ArchivedPost INSERT (DELETE 없음)
- Step 2: ArchivedPost.id 를 기준으로 Post 일괄 DELETE

장점: Step 1 동안 *데이터가 변하지 않으므로* 페이징이 정확.
단점: Step 사이의 *트랜잭션 분리* → 중간 실패 시 정합성 처리 필요.

### 옵션 C — Keyset Pagination (학습자 선택)

장점: 실무에서 가장 권장되는 정석. 페이지 깊이와 무관한 성능.
단점: *커스텀 Reader* 작성 필요.

---

## 9. 디버깅 함정 — *도구의 표시 제한*

학습 중 마주친 *함정의 함정*:

H2 콘솔의 *최대 출력 1000건* 제한. 3000건 데이터가 있는데 콘솔에서 *1000건만 보여* *데이터가 사라진 것처럼* 보임.

> **메타 통찰**: *"내가 본 것 ≠ 실제 상태"*. 디버깅 도구의 *기본 표시 제한* 을 의식 못하면 *허상의 문제* 를 추적하게 됨.

실무에서도 흔한 패턴:
- 모니터링 대시보드가 *상위 N 건만* 보여주는데 그걸 *전체* 로 착각
- 로그 뷰어가 *최근 N 줄만* 보여주는데 *전체* 로 착각

**항상 도구의 표시 제한을 의식**.

---

## 10. 메타 통찰

### 상태를 어떻게 표현할 것인가

문제 해결의 본질:

- **위치 기반 (OFFSET)**: 데이터가 변하면 의미가 변함
- **값 기반 (Keyset)**: *id 같은 절대 식별자* 위에서 진행. 변화에 면역

> **같은 진행을 *위치 (page)* 로 표현하느냐, *값 (lastSeenId)* 으로 표현하느냐. 표현 방식에 따라 *대응할 수 있는 변화* 가 달라짐.**

이건 *상태 모델링* 의 본질적 통찰. *값과 위치의 차이* 가 *시스템의 견고함* 을 결정하는 사례.

### 일반화

이 통찰은 *Spring Batch 만의 이야기가 아님*. 다양한 시스템에서 같은 결로 작동:

- **Kafka 의 offset 관리**: *topic 안의 절대 위치* (offset) 가 변하지 않는 값. Producer/Consumer 가 동시 작동해도 *consumer 의 offset 은 자기 위치 기반으로 진행*.
- **DB 의 WAL (Write-Ahead Log)**: *log sequence number (LSN)* 가 절대 식별자. DB 가 변해도 LSN 은 늘 증가.
- **HTTP API 의 페이지네이션**: 실무에서 `?after=lastSeenId` 패턴이 `?offset=N` 보다 권장됨.

> *값으로 진행하라.* 같은 결의 원칙이 *여러 시스템에 공통적으로 적용*.

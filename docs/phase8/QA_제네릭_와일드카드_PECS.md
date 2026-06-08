# 자바 제네릭과 와일드카드 — PECS 원칙

## 질문 배경

Phase 8 의 ItemWriter 구현 중 IDE 가 자동으로 다음 시그니처를 생성:

```java
return chunk -> {
    List<? extends ArchivedPost> items = chunk.getItems();  // ← ? extends 가 왜?
    ...
};
```

`? extends` 를 지우면 컴파일 에러. *왜 이 와일드카드가 필요한가* 의 의문에서 출발.

이 의문이 *자바 제네릭의 본질* 과 *PECS 원칙* 을 짚는 좋은 학습 기회가 됨.

---

## 1. 제네릭의 본질 — *컴파일 타임의 일*

### 왜 제네릭이 만들어졌는가

자바 1.4 이전에는 컬렉션이 *Object 만 다룰 수 있었다*:

```java
List list = new ArrayList();
list.add("hello");
list.add(123);  // 컴파일 통과, 의도치 않은 타입 섞임
String s = (String) list.get(1);  // 런타임 ClassCastException
```

자바 5+ 에서 제네릭 도입:

```java
List<String> list = new ArrayList<>();
list.add("hello");
list.add(123);  // ← 컴파일 에러
String s = list.get(0);  // 캐스팅 불필요
```

### 제네릭의 핵심

> **"컴파일 타임에 타입 안전성 보장 + 런타임 캐스팅 제거."**

같은 코드를 *여러 타입에 재사용* (확장성) 하면서도, *각 사용처에서는 타입이 고정* (안전성).

---

## 2. 타입 소거 (Type Erasure) — *런타임에는 사라진다*

제네릭은 **컴파일 타임에만 존재**. 런타임에는 *사라집니다*.

```java
// 소스 코드
List<String> strings = new ArrayList<>();
List<Integer> integers = new ArrayList<>();

// 컴파일 후 (바이트코드 수준)
List strings = new ArrayList();   // ← <String> 사라짐
List integers = new ArrayList();  // ← <Integer> 사라짐
```

→ 런타임에는 `List<String>` 과 `List<Integer>` 가 *같은 `List`*. 제네릭은 *컴파일러가 사용하는 타입 검사용 메타정보*.

### 타입 소거의 함의

- *런타임에 제네릭 타입을 알 수 없음*: `if (list instanceof List<String>)` 같은 코드 *불가능*
- *제네릭 배열 생성 불가*: `new T[10]` 안 됨
- *기존 코드와의 호환성*: 자바 5 이전 코드가 자바 5+ 컴파일러로 동작 가능

→ *런타임에는 그냥 Object*. 제네릭은 *컴파일 시점의 "타입 도장"*.

---

## 3. 자바 제네릭의 *불공변성 (Invariance)*

이게 와일드카드의 필요성을 이해하는 출발점.

### 직관과 다른 동작

```java
class Animal {}
class Dog extends Animal {}

Dog dog = new Dog();
Animal animal = dog;  // ✅ OK. Dog 는 Animal 이다

List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs;  // ❌ 컴파일 에러!
```

→ *Dog 가 Animal 이어도 List<Dog> 는 List<Animal> 이 아니다*. 이게 **불공변성 (Invariance)**.

### 왜 불공변인가 — *안전을 위해*

만약 `List<Dog>` 가 `List<Animal>` 의 하위 타입이라면:

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs;  // (가정) 만약 허용된다면
animals.add(new Cat());        // Cat 은 Animal 이니까 add 가능
Dog d = dogs.get(0);            // ← Cat 이 튀어나옴! 런타임 에러
```

→ 타입 안전성이 깨짐. 그래서 *자바는 불공변* 으로 막아둠.

---

## 4. 와일드카드 3종 — *공변성을 안전하게 부여*

`<T>` 와 `<?>` 의 차이부터:

- `<T>`: **타입 파라미터** — 클래스/메서드 선언에서 *타입을 변수처럼* 다룰 때
- `<?>`: **와일드카드** — *어떤 타입이든* 받을 수 있음을 표현

### 와일드카드 3종

```java
List<?>                  // unbounded wildcard
List<? extends Animal>   // upper bounded wildcard
List<? super Dog>        // lower bounded wildcard
```

| 표현 | 의미 | 예 |
|--|--|--|
| `<?>` | *어떤 타입이든* | `List<String>`, `List<Animal>` 모두 가능 |
| `<? extends Animal>` | Animal **이거나 Animal 의 하위 타입** | `List<Animal>`, `List<Dog>`, `List<Cat>` 가능 |
| `<? super Dog>` | Dog **이거나 Dog 의 상위 타입** | `List<Dog>`, `List<Animal>`, `List<Object>` 가능 |

---

## 5. *? extends* 가 *읽기 전용* 인 이유

학습자가 가장 헷갈리는 부분.

### 핵심 — `?` 의 진짜 의미

> **`?` = "이미 결정된 어떤 한 구체적 타입인데, 컴파일러가 그게 뭔지 모름."**

*잡탕 (여러 타입이 섞임)* 이 아니라 **"한 종류인데 정체를 모름"**.

### 시뮬레이션 — 꺼낼 때 (읽기)

```java
void doSomething(List<? extends Animal> list) {
    Animal a = list.get(0);  // ← 안전
}
```

- 실제가 `List<Dog>` 였다면? → `get(0)` 결과는 Dog. Dog 는 Animal. → Animal 변수에 OK.
- 실제가 `List<Cat>` 였다면? → `get(0)` 결과는 Cat. Cat 도 Animal. → Animal 변수에 OK.

→ *무엇이든 Animal 의 하위 타입인 게 보장* → **읽기 안전**.

### 시뮬레이션 — 넣을 때 (쓰기)

```java
void doSomething(List<? extends Animal> list) {
    list.add(new Dog());  // ← ???
}
```

- 실제가 `List<Cat>` 였다면? → **Cat 리스트에 Dog 를 넣는 셈. 타입 안전성 깨짐!**
- 실제가 `List<Animal>` 였다면? → Animal 자리에 Dog. OK.

→ *실제 타입이 뭔지 모르기 때문에 어떤 것도 안전하게 넣을 수 없음* → **쓰기 금지**.

(예외: `null` 은 모든 타입의 값이므로 넣을 수 있음. 다만 의미 없음.)

### 결론

> **`? extends T` 는 *읽기 전용 (Producer)*. 데이터를 *공급* 하는 입장.**

---

## 6. *? super* 의 데칼코마니 — *쓰기 전용*

```java
List<? super Dog> list = ???;  // List<Dog>, List<Animal>, List<Object> 중 하나
```

### 넣을 때 — **안전**

```java
list.add(new Dog());
```

- 실제가 `List<Dog>` 였다면? → Dog 자리에 Dog. OK.
- 실제가 `List<Animal>` 였다면? → Animal 자리에 Dog (Dog 는 Animal). OK.
- 실제가 `List<Object>` 였다면? → Object 자리에 Dog. OK.

→ *무엇이든 Dog 의 상위 타입* 이므로 *Dog 는 항상 넣을 수 있음*.

### 꺼낼 때 — **거의 불가능**

```java
Dog d = list.get(0);
```

- 실제가 `List<Object>` 였다면? → 나오는 건 Object. Dog 로 캐스팅 안 됨. 에러.

→ *유일하게 안전한 건 Object 로 받는 것*:

```java
Object o = list.get(0);  // ← 이건 항상 OK
```

→ **쓰기 가능, 읽기는 사실상 불가** (Object 로만).

---

## 7. PECS 원칙 (Producer Extends, Consumer Super)

자바의 황금 규칙:

| 역할 | 와일드카드 | 의미 |
|--|--|--|
| **Producer** (데이터 *공급*) | `? extends T` | *읽기 전용*. 누가 데이터를 *꺼내 쓰는* 입장 |
| **Consumer** (데이터 *소비*) | `? super T` | *쓰기 전용*. 누가 데이터를 *받아 저장하는* 입장 |
| 둘 다 | `T` (와일드카드 없이) | 양방향 |

암기법: **PECS** — *Producer Extends, Consumer Super*.

---

## 8. ItemWriter 의 `Chunk<? extends T>` 분석

이제 학습자가 본 코드의 의미가 드러납니다.

### Spring Batch 의 `ItemWriter` 시그니처

```java
@FunctionalInterface
public interface ItemWriter<T> {
    void write(Chunk<? extends T> chunk) throws Exception;
}
```

→ `ItemWriter<ArchivedPost>` 의 `write` 는 `Chunk<? extends ArchivedPost>` 를 받음.

### 왜 그렇게 설계됐는가

`ItemWriter` 는 *Chunk 에서 데이터를 꺼내 쓰는 입장* (Producer 측). **PECS 의 Producer Extends 적용**.

이렇게 하면:
- `ItemWriter<ArchivedPost>` 가 *`Chunk<ArchivedPost>`* 도 받고
- 만약 *`MyArchivedPost extends ArchivedPost`* 같은 하위 타입이 있다면 *`Chunk<MyArchivedPost>`* 도 받음

→ *공변성 확보 = 다양한 하위 타입을 받을 수 있는 유연성*.

### 학습자가 `? extends` 를 지웠을 때 에러난 이유

```java
List<ArchivedPost> items = chunk.getItems();  // ❌ 에러
```

`chunk` 의 타입이 `Chunk<? extends ArchivedPost>` 이고 `getItems()` 가 `List<? extends T>` 를 반환하므로 *`List<? extends ArchivedPost>`*. 이걸 *`List<ArchivedPost>`* 변수에 대입하려면 *불공변성에 위배* — 컴파일 에러.

학습자가 `? extends ArchivedPost` 그대로 두면 *공변 그릇* 으로 받아서 *읽기는 가능*. `saveAll` 의 시그니처 `<S extends T> Iterable<S> saveAll(Iterable<S>)` 가 *`Iterable<S extends ArchivedPost>`* 를 받을 수 있으므로 동작.

### 왜 학습자 코드가 "읽기만" 했는가

```java
List<? extends ArchivedPost> items = chunk.getItems();

archivedPostRepository.saveAll(items);  // ← items 를 "꺼내서" saveAll 에 전달

List<Long> originalIds = items.stream()
    .map(ArchivedPost::getId)  // ← items 에서 "꺼내서" 변환
    .toList();
postRepository.deleteAllByIdInBatch(originalIds);
```

학습자가 *items 에 무언가를 add 하지 않았기 때문에* `? extends` 그릇으로도 충분했던 것. PECS 가 적용된 코드.

---

## 9. 학습자의 직관 재구성

학습자가 처음 표현:
> *"? : 아무거나 올 수 있는데, extends Animal: Animal 을 본인이거나 상속한 하위 타입 중에서 아무거나"*

이걸 진짜 의미로 다시 쓰면:

> **`List<? extends Animal>` = "어떤 한 종류 동물의 리스트인데, 그 종류가 정확히 뭔지는 컴파일러가 모름. 단지 Animal 의 하위 타입이라는 것만 보장."**

- *꺼낼 때*: 어쨌든 Animal 인 건 보장 → 안전
- *넣을 때*: 강아지 리스트일지 고양이 리스트일지 모르니 *무엇을 넣어도 잘못된 종류일 가능성* → 금지

---

## 10. 결론 — *읽기 전용은 목적이 아니라 결과*

학습자의 표현:
> *"'Animal 을 상속 받을 수 있는 아무거나' 라는 개념을 구현하려고 보니 쓰기 기능을 켜두면 타입 안정성이 깨진다. 그러니까 읽기 전용으로 쓸 수 있게 하자."*

→ 정확. 작은 정밀화:

> **`? extends T` 는 "공변성을 부여하면서, 컴파일러가 안전을 보장할 수 있는 동작만 허용한 결과"**. 읽기 전용은 *목적* 이 아니라 *자연스러운 귀결*.

### 비유

*안전 규제* 같은 것. 정부가 *"읽기 전용 차량을 만들자"* 로 시작한 게 아니라, *"여러 종류 차량을 한 차고에 둘 수 있게 하자"* 가 목적인데, 안전 검사를 해보니 *"차종을 모르면 새 차를 들여놓는 건 위험하니 차고 출입(쓰기)은 금지"* 가 자연스러운 결론.

---

## 11. 메타 통찰

### 자바의 "이상한 법칙" 들의 공통점

> **자바의 많은 "이상한 법칙" 들은 *타입 안전성을 보장하기 위한 컴파일러의 보수적 판단* 이다.**

*"실제 타입이 뭔지 모르는 상황에서, 무엇이 깨질 수 있는가?"* 로 거꾸로 추론하면 대부분의 법칙이 자연스럽게 도출됨.

### 학습 깊이의 균형

지금 충분한 수준:
- 제네릭은 컴파일 타임 안전성
- 와일드카드 3종 (`?`, `? extends`, `? super`) 의 의미
- PECS 의 직관 — *읽기는 extends, 쓰기는 super*

나중에 다시 볼 깊이:
- *제네릭 메서드* (`<T> T method(T arg)`) 의 활용
- *재귀적 타입 파라미터* (`<T extends Comparable<T>>`)
- *바운디드 타입 파라미터* vs *와일드카드* 의 선택 기준
- *타입 추론* 의 한계와 명시적 타입 지정

본격적으로 *Collections API 를 깊이 다루거나 라이브러리를 만들 때* 다시 보면 됨.

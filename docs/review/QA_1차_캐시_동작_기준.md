# 1차 캐시의 동작 기준

## Q. 1차 캐시는 ID 기반 단건 조회에서만 작동하는가? 어떤 기준이 있는가?

**A:** 1차 캐시가 작동하는 기준은 **"영속성 컨텍스트에 이미 해당 엔티티가 존재하는가?"** 이다.

1차 캐시는 `Map<ID, Entity>` 구조로, **엔티티의 ID를 키**로 사용한다. 따라서 캐시 히트가 일어나려면:

- 같은 트랜잭션 안에서
- 같은 ID의 엔티티가 이미 영속 상태여야 한다

---

## 캐시가 활용되는 경우들

```java
// 1. findById 재조회 (가장 대표적)
Post post1 = postRepository.findById(1L);  // DB 쿼리
Post post2 = postRepository.findById(1L);  // 캐시에서 반환

// 2. save 후 조회
postRepository.

save(newPost);              // INSERT + 영속 상태로 등록

Post found = postRepository.findById(newPost.getId());  // 캐시에서 반환

// 3. findAll 후 단건 조회
List<Post> posts = postRepository.findAll();  // DB 쿼리, 결과 전부 영속 상태로 등록
Post post = postRepository.findById(1L);      // 캐시에서 반환
```

어떤 경로든 해당 ID의 엔티티가 이미 영속 상태면 DB를 거치지 않는다.

---

## 캐시가 활용되지 않는 경우

```java
// JPQL/쿼리 메서드는 항상 DB에 쿼리를 보냄
postRepository.findAll();                    // 매번 DB
postRepository.findByTitle("제목");          // 매번 DB
```

JPQL이나 쿼리 메서드는 조건이 ID가 아니라 제목, 작성자 등이기 때문에 1차 캐시에서 찾을 수 없다. 1차 캐시는 `Map<ID, Entity>` 구조라서 ID로만 조회가 가능하다.

---

## 정리

1차 캐시는 **ID를 키로 하는 Map**이므로, **ID 기반 조회**에서만 캐시 히트가 발생한다. `findById()`만 해당되는 것이 아니라, save, findAll 등 어떤 경로로든 영속 상태가 된
엔티티는 이후 ID 조회 시 캐시에서 반환된다.

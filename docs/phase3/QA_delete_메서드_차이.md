# deleteById vs delete(entity)

## Q. deleteById(id)와 delete(entity)의 차이는?

**A:**
- `deleteById(id)` — 내부에서 `findById()` (SELECT) + `delete()` (DELETE). 총 2단계.
- `delete(entity)` — Entity가 이미 영속성 컨텍스트에 있으면 바로 DELETE 실행. 추가 SELECT 없음.

이미 `findById()`로 Entity를 조회한 상태라면 `delete(entity)`가 SELECT 쿼리 1회를 아낀다. `deleteById()`를 쓰면 이미 조회한 Entity가 있는데 또 SELECT를 하는 셈이므로 쿼리가 낭비된다.

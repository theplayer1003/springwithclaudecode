# 도전과제 — Q&A

## Q1: 쿼리 메서드는 메서드 이름만 지켜주면 스프링이 자동으로 구현체를 만들어주는 건가요? IDE가 보여준 긴 목록은 이미 완성된 메서드인가요?

맞다. 메서드 이름을 규칙에 맞게 선언하면 스프링이 이름을 파싱해서 SQL을 자동 생성하고 구현체를 만들어준다.

```
findByTitleContaining(String keyword)
  → find / By / Title / Containing
  → 조회 / 조건 / 필드명 / LIKE 연산
  → SELECT * FROM post WHERE title LIKE '%keyword%'
```

IDE가 보여준 목록은 "이미 완성된 메서드"가 아니라, 규칙에 맞는 어떤 조합이든 스프링이 해석할 수 있다는 뜻이다. 필드명과 키워드(Containing, After, Between 등)를 자유롭게 조합하면 된다.

---

## Q2: @PathVariable과 @RequestParam의 차이

|        | @PathVariable | @RequestParam               |
|--------|---------------|-----------------------------|
| 위치     | URL 경로 안      | URL 끝의 `?` 뒤                |
| 예시 URL | `/posts/1`    | `/posts/search?keyword=스프링` |
| 용도     | 특정 리소스를 식별할 때 | 필터, 검색, 옵션을 전달할 때           |

- "어떤 것"을 지정 → PathVariable: `/posts/1`, `/users/hong`
- "어떻게" 필터/검색 → RequestParam: `/posts/search?keyword=스프링`, `/posts?page=2&size=10`

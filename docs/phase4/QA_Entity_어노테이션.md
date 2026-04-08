# Entity 어노테이션 (@GeneratedValue, @Column, @Enumerated)

## Q1. @GeneratedValue의 strategy는 무엇인가?

**A:** 기본키(ID) 값을 어떤 방식으로 자동 생성할지를 지정한다.

| 전략 | 방식 |
|------|------|
| `IDENTITY` | DB의 AUTO_INCREMENT에 맡김. INSERT할 때마다 자동으로 다음 번호 부여 |
| `SEQUENCE` | DB 시퀀스 객체 사용 (Oracle, PostgreSQL) |
| `TABLE` | 별도의 키 생성 테이블 사용 |
| `AUTO` | JPA가 DB 종류에 맞는 전략을 자동 선택 (기본값) |

MySQL, H2에서는 `IDENTITY`가 가장 일반적이다.

## Q2. @Column은 무엇인가?

**A:** DB 테이블의 컬럼(열)에 대한 설정을 지정하는 어노테이션이다.

| 옵션 | 의미 |
|------|------|
| `unique = true` | 중복 불가 |
| `nullable = false` | NULL 불가 |
| `length = 50` | 문자열 최대 길이 (기본 255) |
| `name = "user_name"` | 컬럼 이름 지정 (기본은 필드명 그대로) |

## Q3. @Column을 안 붙이면 DB에 저장이 안 되는가?

**A:** 저장된다. `@Entity` 클래스의 모든 필드는 기본적으로 DB 컬럼에 매핑된다. `@Column`을 안 붙이면 기본 설정(nullable = true, unique = false, length = 255)이 적용될 뿐이다. 기본 설정을 변경하고 싶을 때만 붙이면 된다.

## Q4. @Enumerated(EnumType.STRING)은 무엇인가?

**A:** Java Enum을 DB에 어떤 형태로 저장할지를 지정한다.

- `EnumType.ORDINAL` (기본값) — 0, 1 같은 순서 번호로 저장. Enum에 값이 추가되면 순서가 바뀌어 기존 데이터가 깨질 수 있음.
- `EnumType.STRING` — "USER", "ADMIN" 문자열로 저장. 순서가 바뀌어도 영향 없음.

항상 `STRING`을 사용해야 한다. ORDINAL은 Enum에 새 값을 중간에 추가하면 기존 데이터가 잘못 매핑되는 위험이 있다.

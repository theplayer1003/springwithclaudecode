# @MockBean deprecated와 @MockitoBean

## Q. @MockBean이 deprecated된 이유와 대체 방법은?

**배경:** `PostControllerTest`에서 `@MockBean`을 사용하려 했더니 IDE에서 deprecated 경고가 표시되었다. Spring Boot 3.5.0 환경이다.

**A:** `@MockBean`은 Spring Boot가 자체적으로 만든 어노테이션이었는데, Spring Framework 6.2에서 빈을 오버라이드하는 공식 메커니즘(`@MockitoBean`)이 도입되었다.
프레임워크 레벨에서 표준이 생겼으므로 Boot 쪽의 자체 구현을 정리한 것이다. 기능 자체에 문제가 있었던 것이 아니라, 같은 일을 하는 더 표준적인 방식이 생긴 케이스이다.

```java
// 기존 (deprecated)
import org.springframework.boot.test.mock.mockito.MockBean;
@MockBean
private PostService postService;

// 새로운 방식
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@MockitoBean
private PostService postService;
```

사용법은 동일하며, import 경로만 다르다.

## Q. deprecated란 무엇인가?

**배경:** IDE에서 deprecated 경고를 여러 번 봤지만 정확한 의미와 대응 방법이 궁금했다.

**A:** "앞으로 제거될 예정이니 대안을 사용하라"는 표시다. 보통 다음 단계를 거친다:

```
deprecated 표시 → 몇 버전 동안 유지 (하위 호환) → 이후 메이저 버전에서 제거
```

deprecated가 발생하는 일반적인 이유:

- **더 나은 설계가 나옴** — 기존 방식이 잘못된 건 아니지만, 더 명확하거나 유연한 대안이 등장 (가장 흔한 이유)
- **프레임워크 구조 변경** — 내부 아키텍처가 바뀌면서 기존 API가 맞지 않게 됨
- **보안 이슈** — 가끔 있지만 주된 이유는 아님

deprecated를 바로 지우지 않는 이유는 **하위 호환** 때문이다. 기존 프로젝트가 갑자기 깨지면 안 되므로 유예 기간을 두고 서서히 전환을 유도한다.

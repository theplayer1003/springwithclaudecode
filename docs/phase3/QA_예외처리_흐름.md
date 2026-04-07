# 예외 처리 흐름

## Q. Service에서 직접 ErrorResponse를 반환하면 안 되는가?

**A:** 반환 타입이 다르기 때문에 불가능하다. `getPost`의 반환 타입은 `PostResponse`인데 `ErrorResponse`를 반환할 수 없다.

억지로 하려면 `Object`를 반환하거나 모든 Controller 메서드마다 에러 처리 로직을 반복해야 한다.

예외를 던지면 이 문제가 해결된다:
- **Service** — "못 찾았다"는 사실을 예외로 던지기만 함. HTTP 응답 형식을 전혀 모름.
- **GlobalExceptionHandler** — 예외를 가로채서 ErrorResponse(DTO)로 변환하여 응답.

이점: 에러 응답 형식을 바꿀 때 Handler 한 곳만 수정하면 됨. 이것이 관심사의 분리의 실전 적용이다.

참고: `ErrorResponse`는 예외가 아니라 클라이언트에게 보내는 JSON 응답 형식일 뿐이다.

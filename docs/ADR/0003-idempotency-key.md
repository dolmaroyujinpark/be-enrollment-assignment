# 0003. 결제 확정에 Idempotency-Key 헤더 + DB UNIQUE 채택

- 상태: Accepted

## 맥락
명세는 결제 확정을 외부 PG 연동 없이 단순 상태 변경(PENDING→CONFIRMED)으로 대체해도 된다고 한다. 그래도 실무에서 결제는 멱등성이 필수다 — 클라이언트의 재시도, 더블 클릭, 네트워크 타임아웃 후 재요청 등으로 같은 결제가 두 번 처리되면 안 된다. 이 과제에선 "두 번 CONFIRMED 로 만든다"는 명백한 버그는 FSM 으로 막히지만(이미 CONFIRMED 면 전이 거부), "PENDING 인 신청에 결제 요청이 두 번 동시에" 같은 경합과 "재시도가 안전한가"를 명시적으로 다루고 싶었다.

## 결정
`POST /api/enrollments/{id}/payment` 에 **`Idempotency-Key` 헤더를 필수** 로 한다. `payment_intents.idempotency_key` 에 **UNIQUE 제약**. 처리 흐름:
- 같은 키로 이미 처리된 적이 있으면 → 상태 변경 없이 그 키가 처리한 신청을 그대로 반환(멱등).
- 그 키가 **다른 신청** 에 쓰였으면 → `IDEMPOTENCY_KEY_CONFLICT` 409.
- 신규 키면 → `PaymentIntent` 생성(키 저장) → `Enrollment.confirm()` → `PaymentIntent` COMPLETED.
- 같은 키로 동시에 두 요청이 와서 둘 다 신규로 판단하면 → 둘 중 하나의 INSERT 가 UNIQUE 제약에 걸려 `DataIntegrityViolationException` → 409. 재시도하면 멱등 경로로 들어온다.

## 근거
- 헤더 기반 idempotency-key 는 RESTful API 의 표준 멱등성 패턴(Stripe 등 결제 API 의 관례)이라, 보는 사람이 의도를 바로 안다.
- **DB UNIQUE 제약** 이 동시 경합의 최종 방어선 — 애플리케이션 레벨 체크만으로는 "둘 다 키를 못 봤다" 윈도우가 남는데, UNIQUE 가 그걸 닫는다(defense in depth).
- 키를 `PaymentIntent` 엔티티에 저장 → "이 결제는 이 키로 처리됨"을 추적할 수 있다.

## 결과 (트레이드오프)
- (+) 결제 재시도가 안전하다. 동시 중복 호출도 일관되게 한 번만 처리.
- (−) 클라이언트가 키를 관리해야 한다 — 결제 요청마다 고유 키 생성, 재시도 시에는 같은 키 사용. 헤더 누락 시 400.
- (−) 키↔신청 매핑이 어긋나는 케이스(같은 키를 다른 신청에) 처리 로직이 필요하다(구현됨, `IDEMPOTENCY_KEY_CONFLICT`).
- 현재 구현은 `PaymentIntent` 가 사실상 COMPLETED 만 갖는다(생성 직후 confirm 실패 시 트랜잭션 롤백되어 PENDING/FAILED 상태가 DB 에 남지 않음). PG 연동/비동기 결제로 확장하면 PENDING/FAILED 라이프사이클을 실제로 쓰게 될 것 — 스키마는 이미 그 상태들을 허용한다.

## 검토한 대안
- **상태 기반 멱등(키 없이)** — "이미 CONFIRMED 면 그냥 OK 반환". 결제가 한 번만 일어나는 단순 케이스엔 충분하지만, "같은 PENDING 신청에 두 결제 요청이 동시에" 같은 경합에 약하고, 부분 실패 후 "이 재시도가 그 결제의 재시도인지"를 식별할 수 없다.
- **요청 body 해시를 멱등 키로** — 헤더만큼 명시적이지 않고, 같은 결제를 두 번 의도적으로 보내는 케이스를 구분 못 한다.
- **메모리/캐시 기반 키 저장(Redis 없이 in-process)** — 인스턴스 재시작·다중 인스턴스에 취약. DB 에 저장하는 편이 단순하고 정확.

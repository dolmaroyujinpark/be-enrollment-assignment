# 요구사항 해석 및 설계 결정

명세에 명시되지 않았지만 시스템 구현에 필요한 결정 11건. 각 항목은 "결정 + 한 줄 이유 + 구현 위치".

## 도메인 흐름

```
크리에이터 ─[등록]→ Lecture(DRAFT) ─[OPEN]→ 신청 가능
                                            │
수강생 ─[신청]→ Enrollment(PENDING) ─[결제]→ CONFIRMED ─[7일 내]→ CANCELLED
```

## 비즈니스 규칙 (BR-1 ~ BR-11)

| # | 규칙 | 이유 | 구현 |
|---|---|---|---|
| BR-1 | 강의 신청은 **OPEN 상태만** | DRAFT/CLOSED 는 명세상 "신청 불가" | `EnrollmentService.apply` — `LectureStatus.isOpenForEnrollment()` 가드 → `LECTURE_NOT_OPEN 422` |
| BR-2 | 강의 상태는 **단방향 전이** (DRAFT→OPEN→CLOSED) | 명세 화살표가 단방향. 재오픈 허용 시 정원 일관성 깨짐 | `LectureStatus.canTransitionTo` FSM |
| BR-3 | 동일 사용자 active 신청은 **1개**, CANCELLED 후 재신청 가능 | UX 자연스러움 | `uq_enrollments_active` 부분 UNIQUE 인덱스 |
| BR-4 | 결제 확정은 **PENDING 에서만** | 명세 FSM. 멱등성은 BR 외 — `IDEMPOTENCY_KEY` 헤더 + UNIQUE | `PaymentConfirmService.confirm` |
| BR-5 | 취소는 **PENDING / CONFIRMED 에서만** | 명세 FSM. CANCELLED 재취소 불가 | `EnrollmentStatus.canTransitionTo` |
| BR-6 | CONFIRMED 후 **7일 이내만** 취소, PENDING 은 제한 없음 | 명세 선택 구현 예시. 기간은 `enrollment.refund-window` 설정값 | `EnrollmentService.ensureWithinRefundWindow` → `REFUND_WINDOW_PASSED 409` |
| BR-7 | 정원 = **활성(PENDING+CONFIRMED) 신청 수** | 결제 직전 사용자도 자리 점유로 봐야 공정. 무한 PENDING 방지는 별도 정책 (미구현) | `lectures.enrolled_count` 비정규화 카운터 |
| BR-8 | 대기열은 **만석일 때만** 명시적 등록 (`POST /api/lectures/{id}/waitlist`), 자리 남으면 거부 | 자리 남았는데 대기열 등록은 사용자 의도와 어긋남. 응답 다형성도 회피 | `WaitlistService.join` — `hasAvailableSeat()` 가드 → `WAITLIST_NOT_NEEDED 409` |
| BR-9 | 취소 발생 시 대기열 head 1명 자동 PENDING 승급 (**OPEN 강의만**) | 자연스러운 대기열 동작. CLOSED 는 "신청불가" 라 자동 승급도 차단 | `WaitlistService.promoteNext` + `FOR UPDATE SKIP LOCKED` |
| BR-10 | 신청 본인만 자기 신청 결제·취소 | 인가 | `enrollment.userId == 헤더 userId` → `NOT_ENROLLMENT_OWNER 403` |
| BR-11 | 강의별 수강생/대기열 조회는 **작성 크리에이터만** | 인가 | `lecture.creatorId == 헤더 userId` → `NOT_LECTURE_OWNER 403` |

## 인증·인가

- `X-User-Id` 헤더로 사용자 식별 (명세 허용 — JWT/세션 미구현)
- 상태 변경·권한 조회 API 에 헤더 필수
- 강의 등록은 `CREATOR` 역할 검사
- 헤더 위조 방어는 본 과제 범위 외

## 관련 문서

- 동시성 4-Layer 방어와 락 흐름 — [`CONCURRENCY.md`](CONCURRENCY.md)
- 구현 범위·코드 위치·의도적 미구현 — [`SCOPE.md`](SCOPE.md)

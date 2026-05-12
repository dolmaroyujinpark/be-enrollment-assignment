# 0001. 데이터베이스로 PostgreSQL 채택 (H2 / MySQL 대비)

- 상태: Accepted

## 맥락
명세는 H2 / MySQL / PostgreSQL 중 자유 선택을 허용한다. 이 도메인의 핵심은 ① 정원 동시성(마지막 자리 동시 신청 시 정원 수만 성공) ② 동일 사용자 동일 강의에 active 신청 1개 제한(취소 후 재신청은 허용) ③ 대기열 자동 승급이다.

## 결정
**PostgreSQL 16** 을 사용하고, 로컬/평가자 실행은 Docker Compose 로, 통합 테스트는 Testcontainers 로 실 PostgreSQL 을 띄운다.

## 근거
- **부분 UNIQUE 인덱스** `CREATE UNIQUE INDEX uq_enrollments_active ON enrollments(user_id, lecture_id) WHERE status <> 'CANCELLED'` — "active 신청 1개, CANCELLED 는 이력으로 남기고 재신청 허용"을 DB 레벨에서 그대로 표현한다. MySQL 8.0 은 부분 인덱스를 직접 지원하지 않아 생성 컬럼 + 함수로 우회해야 하고, H2 는 미지원이라 같은 보장을 코드로만 흉내내야 한다.
- **`SELECT … FOR UPDATE SKIP LOCKED`** — 대기열 자동 승급에서 head 한 명만 안전하게 잡는 데 쓴다. MySQL 8.0+ 도 지원하지만 PostgreSQL 이 가장 검증되고 예측 가능하다. H2 는 미지원.
- **row-level 비관 락**(`FOR UPDATE`) 은 셋 다 되지만, PostgreSQL 의 동작이 가장 명확하다.
- 운영 관점: `docker compose up` 한 줄 실행, Testcontainers 호환이 좋다. 채용 평가에서 최신 백엔드 스택 시그널.

## 결과 (트레이드오프)
- (+) 위 기능들을 우회 없이 자연스럽게 사용한다 — 동시성/제약 로직이 DB 스키마에 명시적으로 드러난다.
- (−) H2 in-memory 처럼 "외부 의존성 0으로 즉시 실행"은 안 된다 → Docker Compose 로 보완(`docker compose --profile app up`). 통합 테스트도 Testcontainers 로 실 PostgreSQL 을 띄워야 한다(약간의 실행 시간 + Docker 필요).

## 검토한 대안
- **H2 in-memory** — 의존성 없이 즉시 실행되지만 부분 UNIQUE 인덱스·`SKIP LOCKED` 미지원이라, "취소 후 재신청 허용 + active 1개"와 대기열 승급의 동시성 보장이 DB 레벨에서 무너진다. 동시성이 평가 핵심인 과제에서 이건 본질적 약점.
- **MySQL 8.0** — 부분 인덱스를 생성 컬럼으로 우회해야 해 스키마/코드가 덜 명확. 나머지는 PostgreSQL 과 큰 차이 없음.

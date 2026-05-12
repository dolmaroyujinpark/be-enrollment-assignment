// 동시 신청 부하 테스트 (K6)
// ----------------------------------------------------------------------------
// 정원 N 인 강의에 M 명이 거의 동시에 신청 → 정확히 N 명만 성공(201), 나머지는 정원초과(409),
// 최종 enrolled_count == N 인지 확인한다. (애플리케이션의 비관 락 직렬화가 부하 상황에서도 동작하는지 검증)
//
// 사전 조건:
//   - 앱이 실행 중   : docker compose --profile app up   또는   ./gradlew bootRun
//   - 시드 데이터 존재: 크리에이터 id 1~5, 클래스메이트 id 6~35 (SeedRunner, local/docker 프로필)
//
// 실행:
//   k6 run load-test/enrollment-burst.k6.js
//   k6 run -e BASE_URL=http://localhost:8080 load-test/enrollment-burst.k6.js   # 대상 변경
// ----------------------------------------------------------------------------
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CREATOR_ID = 1;        // 시드의 크리에이터 (1~5)
const CLASSMATE_START = 6;   // 시드의 클래스메이트 첫 id (1~5 는 크리에이터)
const VUS = 30;              // 동시 신청자 수 (시드 클래스메이트 수 이하)
const CAPACITY = 10;         // 강의 정원

const enrollSucceeded = new Counter('enroll_succeeded');
const enrollCapacityRejected = new Counter('enroll_capacity_rejected');

export const options = {
  scenarios: {
    burst: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '30s' },
  },
  thresholds: {
    checks: ['rate==1.0'],          // 모든 check 통과해야 성공 (teardown 의 enrolled_count 검증 포함)
    http_req_failed: ['rate==0.0'],  // 5xx / 네트워크 오류 없어야 함 (201/409/422 는 expectedStatuses 로 정상 처리)
  },
};

function jsonHeaders(userId) {
  return { 'Content-Type': 'application/json', 'X-User-Id': String(userId) };
}

function isoDate(daysFromNow) {
  return new Date(Date.now() + daysFromNow * 86400000).toISOString().slice(0, 10);
}

export function setup() {
  const headers = jsonHeaders(CREATOR_ID);
  const created = http.post(`${BASE_URL}/api/lectures`, JSON.stringify({
    title: 'k6 부하테스트 강의', description: '동시 신청 경쟁', price: 10000,
    capacity: CAPACITY, startDate: isoDate(1), endDate: isoDate(30),
  }), { headers });
  check(created, { 'setup: lecture created (201)': (r) => r.status === 201 });
  const lectureId = created.json('id');

  const opened = http.patch(`${BASE_URL}/api/lectures/${lectureId}/status`, JSON.stringify({ status: 'OPEN' }), { headers });
  check(opened, { 'setup: lecture opened (200)': (r) => r.status === 200 });

  return { lectureId };
}

export default function (data) {
  const userId = CLASSMATE_START + (__VU - 1); // VU 1..VUS → user 6..(5+VUS), 서로 다른 사용자
  const res = http.post(`${BASE_URL}/api/enrollments`, JSON.stringify({ lectureId: data.lectureId }), {
    headers: jsonHeaders(userId),
    responseCallback: http.expectedStatuses(201, 409, 422), // 정원초과(409) 등은 정상 비즈니스 응답
  });
  check(res, { 'enroll: 201 또는 409': (r) => r.status === 201 || r.status === 409 });
  if (res.status === 201) enrollSucceeded.add(1);
  if (res.status === 409) enrollCapacityRejected.add(1);
}

export function teardown(data) {
  const res = http.get(`${BASE_URL}/api/lectures/${data.lectureId}`);
  check(res, {
    'teardown: lecture detail (200)': (r) => r.status === 200,
    [`teardown: enrolledCount == 정원(${CAPACITY})`]: (r) => r.json('enrolledCount') === CAPACITY,
    'teardown: availableSeats == 0': (r) => r.json('availableSeats') === 0,
  });
}

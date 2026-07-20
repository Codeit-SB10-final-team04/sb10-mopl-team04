// 실행 시 `-e BASE_URL=https://...`로 대상 서버만 교체 가능
export const BASE_URL = (
  __ENV.BASE_URL || 'http://localhost:8080'
).replace(/\/+$/, '');

// 모든 HTTP 시나리오에 적용할 최초 성능 기준
export const thresholds = {
  http_req_duration: ['p(95)<500'], // 95% 요청이 500ms 이하
  http_req_failed: ['rate<0.01'],   // 에러율 1% 미만
};

// 각 시나리오에 적용할 공통 부하 패턴
export const stages = [
  {duration: '1m', target: 10},     // Warm-up: 서버 준비
  {duration: '3m', target: 50},     // Normal: 일반 사용량
  {duration: '3m', target: 100},    // Peak: 피크 시간대
  {duration: '2m', target: 150},    // Stress: 한계 탐색
  {duration: '1m', target: 0},      // Cool-down: 정리
];

// 개별 시나리오에서는 `export const options = commonOptions`로 사용
export const commonOptions = {
  stages,
  thresholds,
};

import {check} from 'k6';
import {SharedArray} from 'k6/data';
import http from 'k6/http';
import {Rate} from 'k6/metrics';

import {headersFromSetup, loginAll} from '../../shared/auth.js';
import {
  BASE_URL,
  IS_LOCAL_ACCEPTANCE,
  userJourneyThresholds,
} from '../../shared/config.js';
import {capStageTargets, thinkTime} from '../../shared/helpers.js';

// 테스트 사용자 fixture 로드
const users = new SharedArray('follow-count-api-users', () => {
  const fixture = JSON.parse(open('../../data/users.json'));
  return Array.isArray(fixture.users) ? fixture.users : [];
});

if (users.length < 2) {
  throw new Error(
    '팔로워 수 격리 테스트에는 users.json의 테스트 사용자가 2명 이상 필요합니다.',
  );
}

// 정수형 환경 변수 범위 검증
const integerEnvironment = (name, fallback, min, max) => {
  const value = Number.parseInt(__ENV[name] || String(fallback), 10);

  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${name}은 ${min} 이상 ${max} 이하의 정수여야 합니다.`);
  }

  return value;
};

// B 파트 단일 API 테스트와 동일한 부하 단계
const maxVus = integerEnvironment('FOLLOW_COUNT_MAX_VUS', 70, 1, 500);
const thinkTimeMin = integerEnvironment(
  'FOLLOW_COUNT_THINK_TIME_MIN',
  IS_LOCAL_ACCEPTANCE ? 3 : 1,
  0,
  60,
);
const thinkTimeMax = integerEnvironment(
  'FOLLOW_COUNT_THINK_TIME_MAX',
  IS_LOCAL_ACCEPTANCE ? 6 : 1,
  0,
  60,
);
const expectedMinFollowers = integerEnvironment(
  'FOLLOW_COUNT_EXPECT_MIN',
  0,
  0,
  1000000,
);

if (thinkTimeMin > thinkTimeMax) {
  throw new Error(
    'FOLLOW_COUNT_THINK_TIME_MIN은 FOLLOW_COUNT_THINK_TIME_MAX 이하여야 합니다.',
  );
}

const loadStages = capStageTargets(
  [
    {duration: '30s', target: 25},
    {duration: '1m', target: 50},
    {duration: '2m', target: 70},
    {duration: '1m', target: 70},
    {duration: '30s', target: 0},
  ],
  maxVus,
);

// 팔로워 수 조회 성공률과 응답시간 기준
const followCountSuccess = new Rate('follow_count_success');

export const options = {
  scenarios: {
    follow_count: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: loadStages,
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:follow_count}': [
      ...userJourneyThresholds.http_req_duration,
    ],
    'http_req_failed{endpoint:follow_count}': ['rate<0.05'],
    follow_count_success: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
  },
};

// 준비 요청과 측정 요청의 메트릭 태그 분리
const requestFollowCount = (followeeId, headers, measured = true) =>
  http.get(
    `${BASE_URL}/api/follows/count` +
      `?followeeId=${encodeURIComponent(followeeId)}`,
    {
      headers,
      tags: measured
        ? {
            endpoint: 'follow_count',
            name: 'follow_count',
          }
        : {endpoint: 'follow_count_prepare'},
    },
  );

export function setup() {
  // 고정 사용자 로그인과 팔로워 수 조회 대상 선택
  const actor = users[0];
  const followee = users[1];
  const token = loginAll([actor])[0];

  // 측정 전 팔로워 수 응답 구조 검증
  const prepareResponse = requestFollowCount(
    followee.id,
    headersFromSetup(token),
    false,
  );

  if (prepareResponse.status !== 200) {
    throw new Error(
      `팔로워 수 준비 조회 실패. 상태 코드=${prepareResponse.status}`,
    );
  }

  return {
    followeeId: followee.id,
    token,
  };
}

export default function (data) {
  // 1. 특정 사용자의 팔로워 수 조회 API 단독 호출
  const response = requestFollowCount(
    data.followeeId,
    headersFromSetup(data.token),
  );
  let followerCount = null;

  if (response.status === 200) {
    try {
      followerCount = Number(response.json());
    } catch (error) {
      console.error(`팔로워 수 응답 파싱 실패: ${error.message}`);
    }
  }

  // 2. 응답 상태와 최소 팔로워 수 검증
  const validCount = Number.isInteger(followerCount) && followerCount >= 0;
  const enoughFollowers =
    validCount && followerCount >= expectedMinFollowers;
  const succeeded = check(response, {
    '팔로워 수 조회 성공': (result) => result.status === 200,
    '팔로워 수 응답 형식 정상': () => validCount,
    '최소 테스트 팔로워 수 충족': () => enoughFollowers,
  });

  followCountSuccess.add(succeeded);

  // 3. 반복 요청 간 대기
  thinkTime(thinkTimeMin, thinkTimeMax);
}

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
const users = new SharedArray('conversation-find-api-users', () => {
  const fixture = JSON.parse(open('../../data/users.json'));
  return Array.isArray(fixture.users) ? fixture.users : [];
});

if (users.length < 2) {
  throw new Error(
    '대화방 조회 격리 테스트에는 users.json의 테스트 사용자가 2명 이상 필요합니다.',
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

// 부하 단계
const maxVus = integerEnvironment('CONVERSATION_MAX_VUS', 70, 1, 500);
const thinkTimeMin = integerEnvironment(
  'CONVERSATION_THINK_TIME_MIN',
  IS_LOCAL_ACCEPTANCE ? 30 : 1,
  0,
  60,
);
const thinkTimeMax = integerEnvironment(
  'CONVERSATION_THINK_TIME_MAX',
  IS_LOCAL_ACCEPTANCE ? 60 : 1,
  0,
  60,
);

if (thinkTimeMin > thinkTimeMax) {
  throw new Error(
    'CONVERSATION_THINK_TIME_MIN은 CONVERSATION_THINK_TIME_MAX 이하여야 합니다.',
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

// 대화방 조회 성공률과 준비 요청의 허용 상태 코드
const conversationFindSuccess = new Rate('conversation_find_success');
const findExpectedStatuses = http.expectedStatuses(200, 404);
const measuredFindExpectedStatuses = http.expectedStatuses(200);
const createExpectedStatuses = http.expectedStatuses(201, 409);

export const options = {
  scenarios: {
    conversation_find: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: loadStages,
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:conversation_find}': [
      ...userJourneyThresholds.http_req_duration,
    ],
    'http_req_failed{endpoint:conversation_find}': ['rate<0.05'],
    conversation_find_success: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
  },
};

// 준비 요청과 측정 요청의 메트릭 태그 분리
const findConversation = (targetId, headers, endpoint) =>
  http.get(
    `${BASE_URL}/api/conversations/with` +
      `?userId=${encodeURIComponent(targetId)}`,
    {
      headers,
      responseCallback: endpoint
        ? measuredFindExpectedStatuses
        : findExpectedStatuses,
      tags: endpoint
        ? {
            endpoint: 'conversation_find',
            name: 'conversation_find',
          }
        : {endpoint: 'conversation_find_prepare'},
    },
  );

// 측정 대상 대화방 생성
const prepareConversation = (targetId, headers) => {
  const findResponse = findConversation(targetId, headers, false);

  if (findResponse.status === 200) {
    return findResponse.json('id');
  }

  if (findResponse.status !== 404) {
    throw new Error(
      `대화방 준비 조회 실패. 상태 코드=${findResponse.status}`,
    );
  }

  const createResponse = http.post(
    `${BASE_URL}/api/conversations`,
    JSON.stringify({withUserId: targetId}),
    {
      headers: {
        ...headers,
        'Content-Type': 'application/json',
      },
      responseCallback: createExpectedStatuses,
      tags: {endpoint: 'conversation_create_prepare'},
    },
  );

  if (createResponse.status === 201) {
    const findResponseAfterCreate = findConversation(targetId, headers, false);
    return findResponseAfterCreate.status === 200
      ? findResponseAfterCreate.json('id')
      : null;
  }

  if (createResponse.status === 409) {
    const retryResponse = findConversation(targetId, headers, false);
    return retryResponse.status === 200 ? retryResponse.json('id') : null;
  }

  return null;
};

export function setup() {
  // 고정 사용자 로그인과 대화 상대 선택
  const actor = users[0];
  const target = users[1];
  const token = loginAll([actor])[0];

  // 측정 대상 대화방 준비
  const conversationId = prepareConversation(
    target.id,
    headersFromSetup(token),
  );

  if (!conversationId) {
    throw new Error('대화방 조회 격리 테스트용 대화방 준비 실패');
  }

  return {
    targetId: target.id,
    token,
  };
}

export default function (data) {
  if (IS_LOCAL_ACCEPTANCE) {
    thinkTime(thinkTimeMin, thinkTimeMax);
  }

  // 1. 특정 사용자와의 대화방 조회 API 단독 호출
  const response = findConversation(
    data.targetId,
    headersFromSetup(data.token),
    true,
  );

  // 2. 응답 상태와 대화 상대 검증
  const succeeded = check(response, {
    '대화방 조회 성공': (result) => result.status === 200,
    '대화 상대 일치': (result) =>
      result.status === 200 && result.json('with.userId') === data.targetId,
  });

  conversationFindSuccess.add(succeeded);

  // 3. 반복 요청 간 대기
  if (!IS_LOCAL_ACCEPTANCE) {
    thinkTime(thinkTimeMin, thinkTimeMax);
  }
}

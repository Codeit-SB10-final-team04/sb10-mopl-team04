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
const users = new SharedArray('direct-message-list-api-users', () => {
  const fixture = JSON.parse(open('../../data/users.json'));
  return Array.isArray(fixture.users) ? fixture.users : [];
});

if (users.length < 2) {
  throw new Error(
    'DM 목록 격리 테스트에는 users.json의 테스트 사용자가 2명 이상 필요합니다.',
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
const maxVus = integerEnvironment('DM_LIST_MAX_VUS', 70, 1, 500);
const thinkTimeMin = integerEnvironment(
  'DM_LIST_THINK_TIME_MIN',
  IS_LOCAL_ACCEPTANCE ? 15 : 1,
  0,
  60,
);
const thinkTimeMax = integerEnvironment(
  'DM_LIST_THINK_TIME_MAX',
  IS_LOCAL_ACCEPTANCE ? 30 : 1,
  0,
  60,
);
const pageLimit = integerEnvironment('DM_LIST_LIMIT', 50, 1, 100);
const maxPages = integerEnvironment('DM_LIST_PAGES', 1, 1, 10);
const expectedMinMessages = integerEnvironment(
  'DM_LIST_EXPECT_MIN_MESSAGES',
  1,
  0,
  1000000,
);

if (thinkTimeMin > thinkTimeMax) {
  throw new Error(
    'DM_LIST_THINK_TIME_MIN은 DM_LIST_THINK_TIME_MAX 이하여야 합니다.',
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

// DM 목록 조회 성공률과 대화방 준비 요청의 허용 상태 코드
const directMessageListSuccess = new Rate('direct_message_list_success');
const findConversationExpectedStatuses = http.expectedStatuses(200, 404);
const createConversationExpectedStatuses = http.expectedStatuses(201, 409);

export const options = {
  scenarios: {
    direct_message_list: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: loadStages,
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:direct_message_list}': [
      ...userJourneyThresholds.http_req_duration,
    ],
    'http_req_failed{endpoint:direct_message_list}': ['rate<0.05'],
    direct_message_list_success: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
  },
};

// DM 목록 조회에 사용할 대화방 조회
const findConversation = (targetId, headers) =>
  http.get(
    `${BASE_URL}/api/conversations/with` +
      `?userId=${encodeURIComponent(targetId)}`,
    {
      headers,
      responseCallback: findConversationExpectedStatuses,
      tags: {endpoint: 'dm_list_conversation_find_prepare'},
    },
  );

// DM 목록 조회에 사용할 동일 사용자 쌍의 대화방 준비
const prepareConversation = (targetId, headers) => {
  const findResponse = findConversation(targetId, headers);

  if (findResponse.status === 200) {
    return findResponse.json('id');
  }

  if (findResponse.status !== 404) {
    throw new Error(
      `DM 목록용 대화방 조회 실패. 상태 코드=${findResponse.status}`,
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
      responseCallback: createConversationExpectedStatuses,
      tags: {endpoint: 'dm_list_conversation_create_prepare'},
    },
  );

  if (createResponse.status === 201) {
    return createResponse.json('id');
  }

  if (createResponse.status === 409) {
    const retryResponse = findConversation(targetId, headers);
    return retryResponse.status === 200 ? retryResponse.json('id') : null;
  }

  return null;
};

// 커서 기반 DM 목록 조회 URL 구성
const directMessageListUrl = (
  conversationId,
  cursor = null,
  idAfter = null,
) => {
  let url =
    `${BASE_URL}/api/conversations/${conversationId}/direct-messages` +
    `?limit=${pageLimit}&sortBy=createdAt&sortDirection=DESCENDING`;

  if (cursor && idAfter) {
    url +=
      `&cursor=${encodeURIComponent(cursor)}` +
      `&idAfter=${encodeURIComponent(idAfter)}`;
  }

  return url;
};

// 준비 요청과 측정 요청의 메트릭 태그 분리
const requestDirectMessageList = (
  conversationId,
  headers,
  cursor = null,
  idAfter = null,
  measured = true,
) =>
  http.get(directMessageListUrl(conversationId, cursor, idAfter), {
    headers,
    tags: measured
      ? {
          endpoint: 'direct_message_list',
          name: 'direct_message_list',
        }
      : {endpoint: 'direct_message_list_prepare'},
  });

export function setup() {
  // 고정 사용자 로그인과 DM 대화방 준비
  const actor = users[0];
  const target = users[1];
  const token = loginAll([actor])[0];
  const headers = headersFromSetup(token);
  const conversationId = prepareConversation(target.id, headers);

  if (!conversationId) {
    throw new Error('DM 목록 격리 테스트용 대화방 준비 실패');
  }

  // 측정 전 DM 목록 응답 구조 검증
  const prepareResponse = requestDirectMessageList(
    conversationId,
    headers,
    null,
    null,
    false,
  );

  if (prepareResponse.status !== 200) {
    throw new Error(
      `DM 목록 준비 조회 실패. 상태 코드=${prepareResponse.status}`,
    );
  }

  return {
    conversationId,
    token,
  };
}

export default function (data) {
  if (IS_LOCAL_ACCEPTANCE) {
    thinkTime(thinkTimeMin, thinkTimeMax);
  }

  // 1. 커서 기반 페이지네이션 초기값
  const headers = headersFromSetup(data.token);
  let cursor = null;
  let idAfter = null;

  for (let page = 0; page < maxPages; page += 1) {
    // 2. 설정된 최대 페이지까지 DM 목록 조회
    const response = requestDirectMessageList(
      data.conversationId,
      headers,
      cursor,
      idAfter,
    );
    let body = null;

    if (response.status === 200) {
      try {
        body = response.json();
      } catch (error) {
        console.error(`DM 목록 응답 파싱 실패: ${error.message}`);
      }
    }

    // 3. 응답 구조와 최소 테스트 메시지 수 검증
    const validPage =
      response.status === 200 && body !== null && Array.isArray(body.data);
    const enoughMessages =
      page > 0 ||
      (validPage && Number(body.totalCount) >= expectedMinMessages);
    const succeeded = validPage && enoughMessages;

    check(response, {
      'DM 목록 조회 성공': () => validPage,
      'DM 테스트 메시지 수 충족': () => enoughMessages,
    });
    directMessageListSuccess.add(succeeded);

    if (
      !validPage ||
      !body.hasNext ||
      !body.nextCursor ||
      !body.nextIdAfter
    ) {
      break;
    }

    // 4. 다음 페이지 커서 추출
    cursor = body.nextCursor;
    idAfter = body.nextIdAfter;
  }

  // 5. 반복 요청 간 대기
  if (!IS_LOCAL_ACCEPTANCE) {
    thinkTime(thinkTimeMin, thinkTimeMax);
  }
}

import {check, group} from 'k6';
import {SharedArray} from 'k6/data';
import http from 'k6/http';
import {Rate} from 'k6/metrics';

import {
  authHeaders,
  headersFromSetup,
  login,
  loginAll,
} from '../../shared/auth.js';
import {
  BASE_URL,
  IS_LOCAL_ACCEPTANCE,
  userJourneyStages,
  userJourneyThresholds,
} from '../../shared/config.js';
import {
  capStageTargets,
  thinkTime,
} from '../../shared/helpers.js';

// 모든 VU가 공유하는 테스트 사용자 fixture 로드
const users = new SharedArray('follow-scenario-users', () => {
  const fixture = JSON.parse(open('../../data/users.json'));
  return Array.isArray(fixture.users) ? fixture.users : [];
});

if (users.length < 2) {
  throw new Error(
    '팔로우 시나리오에는 users.json의 테스트 사용자가 2명 이상 필요합니다.',
  );
}

const configuredMaxVus = Number.parseInt(
  __ENV.FOLLOW_MAX_VUS || String(users.length),
  10,
);

if (!Number.isInteger(configuredMaxVus) || configuredMaxVus < 1) {
  throw new Error('FOLLOW_MAX_VUS는 1 이상의 정수여야 합니다.');
}

const followFlowSuccess = new Rate('follow_flow_success');
const findFollowExpectedStatuses = http.expectedStatuses(200, 404);
const cleanupExpectedStatuses = http.expectedStatuses(204, 404);
const activeMaxVus = Math.min(
  configuredMaxVus,
  users.length,
  Math.max(...userJourneyStages.map((stage) => stage.target)),
);

export const options = {
  setupTimeout: IS_LOCAL_ACCEPTANCE ? '5m' : '1m',
  stages: capStageTargets(
    userJourneyStages,
    activeMaxVus,
  ),
  thresholds: {
    ...(IS_LOCAL_ACCEPTANCE ? {
      'http_req_duration{endpoint:user_detail}':
        userJourneyThresholds.http_req_duration,
      'http_req_duration{endpoint:follow_create}':
        ['p(95)<5000'],
      'http_req_duration{endpoint:follow_find_by_me}':
        userJourneyThresholds.http_req_duration,
      'http_req_duration{endpoint:follow_delete}':
        userJourneyThresholds.http_req_duration,
      'http_req_failed{endpoint:user_detail}':
        userJourneyThresholds.http_req_failed,
      'http_req_failed{endpoint:follow_create}':
        userJourneyThresholds.http_req_failed,
      'http_req_failed{endpoint:follow_find_by_me}':
        userJourneyThresholds.http_req_failed,
      'http_req_failed{endpoint:follow_delete}':
        userJourneyThresholds.http_req_failed,
    } : userJourneyThresholds),
    follow_flow_success: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
  },
};

// VU별 실행 사용자와 다음 사용자를 팔로우 대상으로 고정 배정
const actorIndexForVu = (setupData) => {
  if (setupData && setupData.actorTokens) {
    return setupData.actorOffset +
      ((__VU - 1) % setupData.actorTokens.length);
  }

  return (__VU - 1) % users.length;
};

// acceptance 실행과 smoke 실행에서 인증 비용을 측정 구간 밖으로 분리
export const prepareFollow = (actorCount = activeMaxVus, actorOffset = 0) => {
  if (!Number.isInteger(actorCount) || actorCount < 1) {
    throw new Error('actorCount는 1 이상의 정수여야 합니다.');
  }

  if (
    !Number.isInteger(actorOffset) ||
    actorOffset < 0 ||
    actorOffset + actorCount > users.length
  ) {
    throw new Error(
      'actorOffset과 actorCount의 사용자 범위가 올바르지 않습니다.',
    );
  }

  return {
    actorTokens: loginAll(
      users.slice(actorOffset, actorOffset + actorCount),
    ),
    actorOffset,
  };
};

export function setup() {
  return IS_LOCAL_ACCEPTANCE ? prepareFollow() : null;
}

const jsonHeaders = (headers) => ({
  ...headers,
  'Content-Type': 'application/json',
});

// 이전 실행 중단으로 남은 팔로우 관계의 최초 1회 정리
const cleanupStaleFollow = (targetId, headers) => {
  const response = http.get(
    `${BASE_URL}/api/follows/followed-by-me` +
      `?followeeId=${encodeURIComponent(targetId)}`,
    {
      headers,
      responseCallback: findFollowExpectedStatuses,
      tags: {endpoint: 'follow_stale_find'},
    },
  );

  if (response.status === 200) {
    http.del(`${BASE_URL}/api/follows/${response.json('id')}`, null, {
      headers,
      responseCallback: cleanupExpectedStatuses,
      tags: {endpoint: 'follow_stale_cleanup'},
    });
  }
};

export function followScenario(setupData = null) {
  const actorIndex = actorIndexForVu(setupData);
  const actor = users[actorIndex];
  const target = users[(actorIndex + 1) % users.length];
  const tokenIndex = setupData && setupData.actorTokens
    ? (__VU - 1) % setupData.actorTokens.length
    : null;
  const headers = tokenIndex === null
    ? authHeaders(login(actor.email, actor.password))
    : headersFromSetup(setupData.actorTokens[tokenIndex]);
  let followId = null;
  let flowSucceeded = false;

  if (IS_LOCAL_ACCEPTANCE) {
    thinkTime(30, 60);
  }

  // 재실행 시 중복 팔로우 방지를 위한 VU별 초기 정리
  if (__ITER === 0) {
    cleanupStaleFollow(target.id, headers);
  }

  try {
    // 1. 팔로우 대상 사용자의 존재 여부 확인
    const userResponse = group('1. 사용자 상세 조회', () =>
      http.get(`${BASE_URL}/api/users/${target.id}`, {
        headers,
        tags: {endpoint: 'user_detail'},
      }),
    );
    const userFound = check(userResponse, {
      '팔로우 대상 사용자 조회 성공': (response) =>
        response.status === 200 && response.json('id') === target.id,
    });

    if (!userFound) {
      return;
    }

    // 2. 팔로우 관계 생성 및 정리용 식별자 보관
    const createResponse = group('2. 팔로우 생성', () =>
      http.post(
        `${BASE_URL}/api/follows`,
        JSON.stringify({followeeId: target.id}),
        {
          headers: jsonHeaders(headers),
          tags: {endpoint: 'follow_create'},
        },
      ),
    );
    const followCreated = check(createResponse, {
      '팔로우 생성 성공': (response) =>
        response.status === 201 && Boolean(response.json('id')),
    });

    if (!followCreated) {
      return;
    }

    followId = createResponse.json('id');

    // 3. 사용자 행동 대기 후 생성된 팔로우 관계 확인
    thinkTime(1, 3);

    const connectionResponse = group('3. 내 팔로우 관계 조회', () =>
      http.get(
        `${BASE_URL}/api/follows/followed-by-me` +
          `?followeeId=${encodeURIComponent(target.id)}`,
        {
          headers,
          tags: {endpoint: 'follow_find_by_me'},
        },
      ),
    );
    const connectionFound = check(connectionResponse, {
      '내 팔로우 관계 조회 성공': (response) =>
        response.status === 200 &&
        response.json('id') === followId &&
        response.json('followeeId') === target.id,
    });

    // 4. 생성한 팔로우 관계 삭제
    const deleteResponse = group('4. 언팔로우', () =>
      http.del(`${BASE_URL}/api/follows/${followId}`, null, {
        headers,
        tags: {endpoint: 'follow_delete'},
      }),
    );
    const followDeleted = check(deleteResponse, {
      '언팔로우 성공': (response) => response.status === 204,
    });

    if (followDeleted) {
      followId = null;
    }

    flowSucceeded = connectionFound && followDeleted;
  } finally {
    // 중간 실패 이후 다음 반복의 중복 팔로우 방지를 위한 정리
    if (followId) {
      http.del(`${BASE_URL}/api/follows/${followId}`, null, {
        headers,
        responseCallback: cleanupExpectedStatuses,
        tags: {endpoint: 'follow_cleanup'},
      });
    }

    // 조회부터 언팔로우까지 전체 사용자 여정의 성공 여부 집계
    followFlowSuccess.add(flowSucceeded);

  }
}

export default followScenario;

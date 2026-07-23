import {check, sleep} from 'k6';
import {SharedArray} from 'k6/data';
import http from 'k6/http';
import {
  Counter,
  Rate,
  Trend,
} from 'k6/metrics';
import sse from 'k6/x/sse';

import {headersFromSetup, loginAll} from '../../shared/auth.js';
import {
  BASE_URL,
  IS_LOCAL_ACCEPTANCE,
  userJourneyStages,
  userJourneyThresholds,
} from '../../shared/config.js';
import {capStageTargets} from '../../shared/helpers.js';

// 모든 VU가 공유하는 테스트 사용자 fixture 로드
const users = new SharedArray('notification-sse-scenario-users', () => {
  const fixture = JSON.parse(open('../../data/users.json'));
  return Array.isArray(fixture.users) ? fixture.users : [];
});

if (users.length < 2) {
  throw new Error(
    '알림 SSE 시나리오에는 users.json의 테스트 사용자가 2명 이상 필요합니다.',
  );
}

// acceptance 실행에서는 VU별 수신자와 이벤트 발생자의 1:1 분리를 위한 균등 분할
const receiverCount = Math.max(
  1,
  Math.floor(users.length * (IS_LOCAL_ACCEPTANCE ? 0.5 : 0.75)),
);
const receiverUsers = users.slice(0, receiverCount);
const triggerUsers = users.slice(receiverCount);

if (triggerUsers.length === 0) {
  throw new Error('알림 이벤트 발생용 테스트 사용자가 1명 이상 필요합니다.');
}

const integerEnvironment = (name, fallback, max) => {
  const value = Number.parseInt(__ENV[name] || String(fallback), 10);

  if (!Number.isInteger(value) || value < 1 || value > max) {
    throw new Error(`${name}는 1 이상 ${max} 이하의 정수여야 합니다.`);
  }

  return value;
};

const maxSseVus = integerEnvironment(
  'SSE_MAX_VUS',
  receiverUsers.length,
  receiverUsers.length,
);
const eventTimeoutSeconds = integerEnvironment(
  'SSE_EVENT_TIMEOUT_SECONDS',
  IS_LOCAL_ACCEPTANCE ? 120 : 30,
  300,
);
const idleSeconds = integerEnvironment(
  'SSE_IDLE_SECONDS',
  IS_LOCAL_ACCEPTANCE ? 60 : 1,
  300,
);
const triggerUserCount = integerEnvironment(
  'SSE_TRIGGER_USERS',
  IS_LOCAL_ACCEPTANCE
    ? Math.min(maxSseVus, triggerUsers.length)
    : Math.min(10, triggerUsers.length),
  triggerUsers.length,
);
const activeTriggerUsers = triggerUsers.slice(
  0,
  Math.min(triggerUserCount, maxSseVus),
);

// SSE 연결, 이벤트 수신, 전체 알림 흐름과 전달 지연시간 측정 지표
const sseConnectionSuccess = new Rate('sse_connection_success');
const sseNotificationReceived = new Rate('sse_notification_received');
const notificationFlowSuccess = new Rate('notification_flow_success');
const notificationTriggerSuccess = new Rate('notification_trigger_success');
const sseNotificationLatency = new Trend(
  'sse_notification_delivery_latency',
  true,
);
const sseEventsReceived = new Counter('sse_events_received');
const sseErrors = new Counter('sse_errors');
const findFollowExpectedStatuses = http.expectedStatuses(200, 404);
const cleanupFollowExpectedStatuses = http.expectedStatuses(204, 404);

export const options = {
  setupTimeout: IS_LOCAL_ACCEPTANCE ? '5m' : '3m',
  scenarios: {
    notification_sse_connections: {
      executor: 'ramping-vus',
      exec: 'notificationSseScenario',
      stages: capStageTargets(userJourneyStages, maxSseVus),
      gracefulRampDown: IS_LOCAL_ACCEPTANCE ? '70s' : '5s',
      gracefulStop: IS_LOCAL_ACCEPTANCE ? '70s' : '30s',
    },
  },
  thresholds: {
    // setup 인증과 스트림 유지시간을 제외한 HTTP API 기준
    'http_req_duration{endpoint:notification_trigger_follow_find}':
      userJourneyThresholds.http_req_duration,
    'http_req_duration{endpoint:notification_trigger_follow_create}':
      IS_LOCAL_ACCEPTANCE
        ? ['p(95)<10000']
        : userJourneyThresholds.http_req_duration,
    'http_req_duration{endpoint:notification_trigger_follow_cleanup}':
      IS_LOCAL_ACCEPTANCE
        ? ['p(95)<10000']
        : userJourneyThresholds.http_req_duration,
    'http_req_duration{endpoint:notification_list}':
      IS_LOCAL_ACCEPTANCE
        ? ['p(95)<10000']
        : userJourneyThresholds.http_req_duration,
    'http_req_duration{endpoint:notification_read}':
      IS_LOCAL_ACCEPTANCE
        ? ['p(95)<10000']
        : userJourneyThresholds.http_req_duration,
    'http_req_failed{endpoint:notification_trigger_follow_find}':
      userJourneyThresholds.http_req_failed,
    'http_req_failed{endpoint:notification_trigger_follow_create}':
      userJourneyThresholds.http_req_failed,
    'http_req_failed{endpoint:notification_trigger_follow_cleanup}':
      userJourneyThresholds.http_req_failed,
    'http_req_failed{endpoint:notification_list}':
      userJourneyThresholds.http_req_failed,
    'http_req_failed{endpoint:notification_read}':
      userJourneyThresholds.http_req_failed,
    sse_connection_success: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
    sse_notification_received: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
    notification_flow_success: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
    notification_trigger_success: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
    sse_notification_delivery_latency: [
      IS_LOCAL_ACCEPTANCE ? 'p(95)<3000' : 'p(95)<1000',
    ],
  },
};

const headersWithJson = (headers) => ({
  ...headers,
  'Content-Type': 'application/json',
});

const receiverIndexForVu = () => (__VU - 1) % receiverUsers.length;

const findExistingFollow = (receiverId, headers, measured = true) =>
  http.get(
    `${BASE_URL}/api/follows/followed-by-me` +
      `?followeeId=${encodeURIComponent(receiverId)}`,
    {
      headers,
      responseCallback: findFollowExpectedStatuses,
      tags: {
        endpoint: measured
          ? 'notification_trigger_follow_find'
          : 'notification_follow_find_prepare',
      },
    },
  );

// 기존 인증 토큰을 이용한 SSE 사용자 쌍 사전 준비
export const prepareNotificationWithTokens = (
  triggerTokens,
  receiverTokens,
  receiverOffset = 0,
) => {
  if (!Array.isArray(triggerTokens) || triggerTokens.length < 1) {
    throw new Error('triggerTokens는 비어 있지 않은 배열이어야 합니다.');
  }

  if (!Array.isArray(receiverTokens) || receiverTokens.length < 1) {
    throw new Error('receiverTokens는 비어 있지 않은 배열이어야 합니다.');
  }

  if (
    !Number.isInteger(receiverOffset) ||
    receiverOffset < 0 ||
    receiverOffset + receiverTokens.length > receiverUsers.length
  ) {
    throw new Error(
      'receiverOffset과 receiverTokens의 사용자 범위가 올바르지 않습니다.',
    );
  }

  const selectedReceivers = receiverUsers.slice(
    receiverOffset,
    receiverOffset + receiverTokens.length,
  );

  if (IS_LOCAL_ACCEPTANCE) {
    selectedReceivers.forEach((receiver, index) => {
      const triggerHeaders = headersFromSetup(
        triggerTokens[index % triggerTokens.length],
      );
      const existingFollow = findExistingFollow(
        receiver.id,
        triggerHeaders,
        false,
      );

      if (existingFollow.status === 200) {
        const cleanupResponse = http.del(
          `${BASE_URL}/api/follows/${existingFollow.json('id')}`,
          null,
          {
            headers: triggerHeaders,
            responseCallback: cleanupFollowExpectedStatuses,
            tags: {endpoint: 'notification_follow_cleanup_prepare'},
          },
        );

        if (![204, 404].includes(cleanupResponse.status)) {
          throw new Error(
            `SSE 팔로우 사전 정리 실패. 상태 코드=${cleanupResponse.status}`,
          );
        }
      } else if (existingFollow.status !== 404) {
        throw new Error(
          `SSE 팔로우 사전 조회 실패. 상태 코드=${existingFollow.status}`,
        );
      }
    });
  }

  return {
    triggerTokens,
    receiverTokens,
    receiverOffset,
  };
};

// 수신자와 이벤트 발생자 토큰의 최초 1회 발급과 전체 VU 공유
export const prepareNotification = (
  triggerCount = activeTriggerUsers.length,
  activeReceiverCount = maxSseVus,
  receiverOffset = 0,
) => {
  if (!Number.isInteger(triggerCount) || triggerCount < 1) {
    throw new Error('triggerCount는 1 이상의 정수여야 합니다.');
  }

  if (!Number.isInteger(activeReceiverCount) || activeReceiverCount < 1) {
    throw new Error('activeReceiverCount는 1 이상의 정수여야 합니다.');
  }

  if (
    !Number.isInteger(receiverOffset) ||
    receiverOffset < 0 ||
    receiverOffset + activeReceiverCount > receiverUsers.length
  ) {
    throw new Error(
      'receiverOffset과 activeReceiverCount의 사용자 범위가 올바르지 않습니다.',
    );
  }

  const selectedTriggers = activeTriggerUsers.slice(
    0,
    Math.min(triggerCount, activeTriggerUsers.length),
  );
  const selectedReceivers = receiverUsers.slice(
    receiverOffset,
    receiverOffset + Math.min(activeReceiverCount, maxSseVus),
  );
  const tokens = loginAll([...selectedTriggers, ...selectedReceivers]);

  return prepareNotificationWithTokens(
    tokens.slice(0, selectedTriggers.length),
    tokens.slice(selectedTriggers.length),
    receiverOffset,
  );
};

export function setup() {
  return prepareNotification();
}

export function notificationSseScenario(setupData) {
  // 1. 수신자와 VU별 이벤트 발생자 인증 정보 선택
  const receiverTokenIndex = receiverIndexForVu() % setupData.receiverTokens.length;
  const receiverIndex = setupData.receiverOffset + receiverTokenIndex;
  const receiver = receiverUsers[receiverIndex];
  const receiverToken =
    setupData.receiverTokens[receiverTokenIndex];
  const headers = headersFromSetup(receiverToken);
  const triggerToken =
    setupData.triggerTokens[
      receiverTokenIndex % setupData.triggerTokens.length
    ];

  const triggerHeaders = headersFromSetup(triggerToken);
  let connected = false;
  let receivedNotification = null;
  let streamError = null;
  let followId = null;
  let triggerCreated = false;
  let triggerCleaned = false;

  // 2. 수신자 계정의 SSE 스트림 연결
  const response = sse.open(
    `${BASE_URL}/api/sse`,
    {
      method: 'GET',
      headers: {
        Authorization: headers.Authorization,
        Accept: 'text/event-stream',
      },
      tags: {endpoint: 'notification_sse_connect'},
      timeout: `${eventTimeoutSeconds}s`,
    },
    (client) => {
      client.on('open', () => {
        connected = true;

        try {
          // 3. 연결 완료 후 실제 알림 생성을 위한 팔로우 이벤트 발생
          if (!IS_LOCAL_ACCEPTANCE) {
            const existingFollow = findExistingFollow(
              receiver.id,
              triggerHeaders,
            );

            if (existingFollow.status === 200) {
              // 이전 실행 중단으로 남은 동일 팔로우 관계 정리
              http.del(
                `${BASE_URL}/api/follows/${existingFollow.json('id')}`,
                null,
                {
                  headers: triggerHeaders,
                  responseCallback: cleanupFollowExpectedStatuses,
                  tags: {
                    endpoint: 'notification_trigger_follow_cleanup',
                  },
                },
              );
            }
          }

          const createResponse = http.post(
            `${BASE_URL}/api/follows`,
            JSON.stringify({followeeId: receiver.id}),
            {
              headers: headersWithJson(triggerHeaders),
              tags: {endpoint: 'notification_trigger_follow_create'},
            },
          );
          triggerCreated = check(createResponse, {
            '알림 발생용 팔로우 생성 성공': (result) =>
              result.status === 201 && Boolean(result.json('id')),
          });

          if (!triggerCreated) {
            streamError =
              `알림 발생용 팔로우 생성 실패. 상태 코드=${createResponse.status}`;
            sseErrors.add(1);
            client.close();
            return;
          }

          followId = createResponse.json('id');
        } catch (error) {
          streamError = `알림 이벤트 발생 실패: ${error.message}`;
          sseErrors.add(1);
          client.close();
        }
      });

      client.on('event', (event) => {
        // 4. 알림 이벤트만 선별한 현재 수신자 데이터 확인
        if (event.name !== 'notifications') {
          return;
        }

        try {
          const notification =
            typeof event.data === 'string'
              ? JSON.parse(event.data)
              : event.data;

          if (notification.receiverId !== receiver.id) {
            return;
          }

          receivedNotification = notification;
          sseEventsReceived.add(1);

          const createdAt = Date.parse(notification.createdAt);

          if (Number.isFinite(createdAt)) {
            // 알림 생성 시각부터 SSE 수신 시각까지의 전달 지연 측정
            sseNotificationLatency.add(Date.now() - createdAt);
          }

          if (IS_LOCAL_ACCEPTANCE) {
            if (followId) {
              const cleanupResponse = http.del(
                `${BASE_URL}/api/follows/${followId}`,
                null,
                {
                  headers: triggerHeaders,
                  responseCallback: cleanupFollowExpectedStatuses,
                  tags: {
                    endpoint: 'notification_trigger_follow_cleanup',
                  },
                },
              );
              triggerCleaned = check(cleanupResponse, {
                '알림 발생용 팔로우 정리 성공': (result) =>
                  result.status === 204 || result.status === 404,
              });

              if (triggerCleaned) {
                followId = null;
              }
            }

            sleep(idleSeconds);
          }

          client.close();
        } catch (error) {
          streamError = `SSE 알림 파싱 실패: ${error.message}`;
          sseErrors.add(1);
          client.close();
        }
      });

      client.on('error', (error) => {
        streamError = `SSE 연결 오류: ${error.error()}`;
        sseErrors.add(1);
        client.close();
      });
    },
  );

  // 알림 발생에 사용한 팔로우 관계 정리
  if (followId) {
    const cleanupResponse = http.del(
      `${BASE_URL}/api/follows/${followId}`,
      null,
      {
        headers: triggerHeaders,
        responseCallback: cleanupFollowExpectedStatuses,
        tags: {endpoint: 'notification_trigger_follow_cleanup'},
      },
    );
    triggerCleaned = check(cleanupResponse, {
      '알림 발생용 팔로우 정리 성공': (result) =>
        result.status === 204 || result.status === 404,
    });
  }

  const triggerSucceeded = triggerCreated && triggerCleaned;
  const responseSucceeded = response && response.status === 200;
  const eventReceived = Boolean(receivedNotification);

  // 5. SSE 연결, 이벤트 수신, 트리거 성공 여부 집계
  check(response, {
    'SSE 연결 성공': () => responseSucceeded && connected,
    'SSE 알림 이벤트 수신 성공': () => eventReceived,
    'SSE 스트림 오류 없음': () => streamError === null,
  });
  sseConnectionSuccess.add(responseSucceeded && connected);
  sseNotificationReceived.add(eventReceived);
  notificationTriggerSuccess.add(triggerSucceeded);

  if (!eventReceived) {
    // 이벤트 미수신 시 알림 목록과 읽음 처리의 후속 호출 생략
    notificationFlowSuccess.add(false);

    if (streamError) {
      console.error(streamError);
    }

    return;
  }

  // 6. SSE로 수신한 알림의 알림 목록 저장 여부 확인
  const listResponse = http.get(
    `${BASE_URL}/api/notifications` +
      '?limit=20&sortDirection=DESCENDING&sortBy=createdAt',
    {
      headers,
      tags: {endpoint: 'notification_list'},
    },
  );
  const notificationFound = check(listResponse, {
    '알림 목록 조회 성공': (result) => result.status === 200,
    'SSE로 받은 알림 목록 포함': (result) => {
      if (result.status !== 200) {
        return false;
      }

      const data = result.json('data');
      return (
        Array.isArray(data) &&
        data.some((notification) =>
          notification.id === receivedNotification.id
        )
      );
    },
  });

  // 7. 알림 읽음 처리 후 전체 사용자 여정의 성공 여부 집계
  const readResponse = http.del(
    `${BASE_URL}/api/notifications/${receivedNotification.id}`,
    null,
    {
      headers,
      tags: {endpoint: 'notification_read'},
    },
  );
  const notificationRead = check(readResponse, {
    '알림 읽음 처리 성공': (result) => result.status === 204,
  });

  notificationFlowSuccess.add(
    responseSucceeded &&
    connected &&
    eventReceived &&
    triggerSucceeded &&
    notificationFound &&
    notificationRead &&
    !streamError,
  );
}

export default notificationSseScenario;

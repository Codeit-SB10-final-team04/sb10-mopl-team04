import {check, fail} from 'k6';
import {SharedArray} from 'k6/data';
import http from 'k6/http';
import {
  Counter,
  Rate,
  Trend,
} from 'k6/metrics';
import ws from 'k6/ws';

import {headersFromSetup, loginAll} from '../../shared/auth.js';
import {
  BASE_URL,
  IS_LOCAL_ACCEPTANCE,
  userJourneyStages,
  userJourneyThresholds,
} from '../../shared/config.js';
import {
  capStageTargets,
  randomIntBetween,
} from '../../shared/helpers.js';
import {
  createStompFrame,
  parseSockJsPacket,
  parseStompFrame,
  wrapSockJsMessages,
} from '../../shared/sockjs-stomp.js';

// 모든 VU가 공유하는 테스트 사용자 fixture 로드
const users = new SharedArray('dm-websocket-scenario-users', () => {
  const fixture = JSON.parse(open('../../data/users.json'));
  return Array.isArray(fixture.users) ? fixture.users : [];
});

if (users.length < 2) {
  throw new Error(
    'DM WebSocket 시나리오에는 users.json의 테스트 사용자가 2명 이상 필요합니다.',
  );
}

const integerEnvironment = (name, fallback, min, max) => {
  const value = Number.parseInt(__ENV[name] || String(fallback), 10);

  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${name}은 ${min} 이상 ${max} 이하의 정수여야 합니다.`);
  }

  return value;
};

// 메시지 반복 횟수와 연결 유지시간의 실행 환경별 조정값
const maxVus = integerEnvironment(
  'DM_MAX_VUS',
  users.length,
  1,
  users.length,
);
const messageCountMin = integerEnvironment(
  'DM_MESSAGE_COUNT_MIN',
  IS_LOCAL_ACCEPTANCE ? 1 : 5,
  1,
  20,
);
const messageCountMax = integerEnvironment(
  'DM_MESSAGE_COUNT_MAX',
  IS_LOCAL_ACCEPTANCE ? 1 : 10,
  1,
  20,
);
const idleSeconds = integerEnvironment('DM_IDLE_SECONDS', 30, 1, 300);
const receiveTimeoutSeconds = integerEnvironment(
  'DM_RECEIVE_TIMEOUT_SECONDS',
  IS_LOCAL_ACCEPTANCE ? 30 : 10,
  1,
  60,
);

if (messageCountMin > messageCountMax) {
  throw new Error(
    'DM_MESSAGE_COUNT_MIN은 DM_MESSAGE_COUNT_MAX 이하여야 합니다.',
  );
}

// 연결 성공률, 메시지 교환 성공률, 송수신 지연시간 측정 지표
const dmConnectionSuccess = new Rate('dm_connection_success');
const dmMessageExchangeSuccess = new Rate('dm_message_exchange_success');
const dmMessageLatency = new Trend('dm_message_latency', true);
const dmMessagesSent = new Counter('dm_messages_sent');
const dmMessagesReceived = new Counter('dm_messages_received');
const dmProtocolErrors = new Counter('dm_protocol_errors');
const findConversationExpectedStatuses = http.expectedStatuses(200, 404);
const createConversationExpectedStatuses = http.expectedStatuses(201, 409);

export const options = {
  stages: capStageTargets(userJourneyStages, maxVus),
  setupTimeout: IS_LOCAL_ACCEPTANCE ? '5m' : '3m',
  thresholds: {
    ...(!IS_LOCAL_ACCEPTANCE ? {
      'http_req_duration{endpoint:conversation_find_with_user}':
        userJourneyThresholds.http_req_duration,
      'http_req_duration{endpoint:conversation_create}':
        userJourneyThresholds.http_req_duration,
      'http_req_failed{endpoint:conversation_find_with_user}':
        userJourneyThresholds.http_req_failed,
      'http_req_failed{endpoint:conversation_create}':
        userJourneyThresholds.http_req_failed,
    } : {}),
    dm_connection_success: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
    dm_message_exchange_success: [
      IS_LOCAL_ACCEPTANCE ? 'rate>0.95' : 'rate>0.99',
    ],
    dm_message_latency: [
      IS_LOCAL_ACCEPTANCE ? 'p(95)<3000' : 'p(95)<1000',
    ],
  },
};

// 기존 인증 토큰을 이용한 대화방 사전 준비
export const prepareDmWithTokens = (actorTokens, actorOffset = 0) => {
  if (!Array.isArray(actorTokens) || actorTokens.length < 1) {
    throw new Error('actorTokens는 비어 있지 않은 배열이어야 합니다.');
  }

  if (
    !Number.isInteger(actorOffset) ||
    actorOffset < 0 ||
    actorOffset + actorTokens.length > users.length
  ) {
    throw new Error('actorOffset과 actorTokens의 사용자 범위가 올바르지 않습니다.');
  }

  const selectedUsers = users.slice(
    actorOffset,
    actorOffset + actorTokens.length,
  );
  const conversationIds = IS_LOCAL_ACCEPTANCE
    ? selectedUsers.map((actor, index) => {
        const actorIndex = actorOffset + index;
        const target = users[(actorIndex + 1) % users.length];
        const conversationId = prepareConversation(
          target.id,
          headersFromSetup(actorTokens[index]),
          false,
        );

        if (!conversationId) {
          throw new Error(
            `${actor.email} 사용자의 DM 대화방 사전 준비 실패`,
          );
        }

        return conversationId;
      })
    : null;

  return {
    actorTokens,
    actorOffset,
    conversationIds,
  };
};

// 측정 요청에서 인증 비용을 제외하기 위한 사용자 토큰 사전 발급
export const prepareDm = (actorCount = maxVus, actorOffset = 0) => {
  if (!Number.isInteger(actorCount) || actorCount < 1) {
    throw new Error('actorCount는 1 이상의 정수여야 합니다.');
  }

  if (
    !Number.isInteger(actorOffset) ||
    actorOffset < 0 ||
    actorOffset + actorCount > users.length
  ) {
    throw new Error('actorOffset과 actorCount의 사용자 범위가 올바르지 않습니다.');
  }

  const selectedUsers = users.slice(
    actorOffset,
    actorOffset + Math.min(actorCount, maxVus),
  );

  return prepareDmWithTokens(loginAll(selectedUsers), actorOffset);
};

export function setup() {
  return prepareDm();
}

// VU별 발신자와 다음 사용자를 수신자로 고정 배정
const actorIndexForVu = (setupData) =>
  setupData.actorOffset + ((__VU - 1) % setupData.actorTokens.length);

const authenticatedJsonHeaders = (headers) => ({
  ...headers,
  'Content-Type': 'application/json',
});

const conversationWith = (targetId, headers, measured = true) => {
  const response = http.get(
    `${BASE_URL}/api/conversations/with` +
      `?userId=${encodeURIComponent(targetId)}`,
    {
      headers,
      responseCallback: findConversationExpectedStatuses,
      tags: {
        endpoint: measured
          ? 'conversation_find_with_user'
          : 'dm_conversation_find_prepare',
      },
    },
  );

  return response.status === 200 ? response.json('id') : null;
};

// 기존 대화방 우선 조회 후 미존재 시 신규 대화방 생성
const prepareConversation = (targetId, headers, measured = true) => {
  const existingConversationId = conversationWith(
    targetId,
    headers,
    measured,
  );

  if (existingConversationId) {
    return existingConversationId;
  }

  const createResponse = http.post(
    `${BASE_URL}/api/conversations`,
    JSON.stringify({withUserId: targetId}),
    {
      headers: authenticatedJsonHeaders(headers),
      responseCallback: createConversationExpectedStatuses,
      tags: {
        endpoint: measured
          ? 'conversation_create'
          : 'dm_conversation_create_prepare',
      },
    },
  );

  if (createResponse.status === 201) {
    return createResponse.json('id');
  }

  if (createResponse.status === 409) {
    // 다른 VU의 동시 생성 완료 가능성을 반영한 대화방 재조회
    return conversationWith(targetId, headers, measured);
  }

  return null;
};

const webSocketBaseUrl = BASE_URL
  .replace(/^http:/, 'ws:')
  .replace(/^https:/, 'wss:');
const stompHost = BASE_URL
  .replace(/^https?:\/\//, '')
  .split('/')[0];

// SockJS WebSocket transport 규격에 맞춘 연결 URL 생성
const sockJsWebSocketUrl = () => {
  const serverId = String((__VU + __ITER) % 1000).padStart(3, '0');
  const randomPart = Math.random().toString(36).slice(2, 10);
  const sessionId = `${__VU.toString(36)}${__ITER.toString(36)}${randomPart}`
    .slice(0, 8)
    .padEnd(8, '0');

  return `${webSocketBaseUrl}/ws/${serverId}/${sessionId}/websocket`;
};

// STOMP 프레임 생성 후 SockJS 클라이언트 메시지 형식으로 전송
const sendStomp = (socket, command, headers = {}, body = '') => {
  socket.send(
    wrapSockJsMessages(createStompFrame(command, headers, body)),
  );
};

export function dmWebSocketScenario(setupData) {
  // 1. 발신자 인증 정보 선택 및 대상 사용자와의 대화방 준비
  const actorIndex = actorIndexForVu(setupData);
  const actor = users[actorIndex];
  const target = users[(actorIndex + 1) % users.length];
  const actorToken = setupData.actorTokens[
    (__VU - 1) % setupData.actorTokens.length
  ];
  const accessToken = actorToken.accessToken;
  const headers = headersFromSetup(actorToken);
  const conversationId = setupData.conversationIds
    ? setupData.conversationIds[
        (__VU - 1) % setupData.conversationIds.length
      ]
    : prepareConversation(target.id, headers);

  if (!conversationId) {
    fail(
      `${actor.email} 사용자의 DM 대화방을 준비하지 못했습니다.`,
    );
  }

  // 반복 송수신 상태와 지연시간 계산을 위한 VU 내부 상태
  const subscriptionId = `dm-${__VU}-${__ITER}`;
  const messageCount = randomIntBetween(
    messageCountMin,
    messageCountMax,
  );
  const sentAtByContent = {};
  let sentCount = 0;
  let receivedCount = 0;
  let stompConnected = false;
  let idleScheduled = false;
  let closeStarted = false;
  let receiveTimedOut = false;
  let protocolError = null;

  const response = ws.connect(
    sockJsWebSocketUrl(),
    {
      tags: {endpoint: 'dm_sockjs_websocket'},
    },
    (socket) => {
      // 정상 종료 시 구독 해제와 STOMP 연결 해제를 우선 전송
      const closeGracefully = () => {
        if (closeStarted) {
          return;
        }

        closeStarted = true;

        if (stompConnected) {
          sendStomp(socket, 'UNSUBSCRIBE', {id: subscriptionId});
          sendStomp(socket, 'DISCONNECT', {
            receipt: `disconnect-${subscriptionId}`,
          });
        }

        socket.setTimeout(() => socket.close(), 100);
      };

      // 전체 메시지 수신 이후 설정된 idle 시간 유지
      const scheduleIdleClose = () => {
        if (idleScheduled) {
          return;
        }

        idleScheduled = true;
        socket.setTimeout(closeGracefully, idleSeconds * 1000);
      };

      // 2. SEND 프레임 전송 및 다음 메시지 예약
      const sendNextMessage = () => {
        if (sentCount >= messageCount || closeStarted) {
          return;
        }

        sentCount += 1;
        const content =
          `k6-dm-vu${__VU}-iter${__ITER}` +
          `-message${sentCount}-${Date.now()}`;
        sentAtByContent[content] = Date.now();

        sendStomp(
          socket,
          'SEND',
          {
            destination:
              `/pub/conversations/${conversationId}/direct-messages`,
            'content-type': 'application/json',
          },
          JSON.stringify({content}),
        );
        dmMessagesSent.add(1);

        if (sentCount < messageCount) {
          socket.setTimeout(
            sendNextMessage,
            randomIntBetween(2, 5) * 1000,
          );
          return;
        }

        // 마지막 메시지 전송 이후 전체 응답의 수신 완료 대기
        socket.setTimeout(() => {
          if (receivedCount < messageCount) {
            receiveTimedOut = true;
            scheduleIdleClose();
          }
        }, receiveTimeoutSeconds * 1000);
      };

      const handleStompFrame = (rawFrame) => {
        const frame = parseStompFrame(rawFrame);

        if (frame.command === 'CONNECTED') {
          // 3. STOMP 연결 완료 후 대화방 구독과 첫 메시지 전송
          stompConnected = true;
          sendStomp(socket, 'SUBSCRIBE', {
            id: subscriptionId,
            destination:
              `/sub/conversations/${conversationId}/direct-messages`,
            ack: 'auto',
          });
          socket.setTimeout(sendNextMessage, 100);
          return;
        }

        if (frame.command === 'MESSAGE') {
          // 4. 자신이 보낸 메시지 수신 여부와 왕복 지연시간 확인
          let directMessage;

          try {
            directMessage = JSON.parse(frame.body);
          } catch (error) {
            protocolError = `DM 응답 JSON 파싱 실패: ${error.message}`;
            dmProtocolErrors.add(1);
            closeGracefully();
            return;
          }

          const sentAt = sentAtByContent[directMessage.content];

          if (sentAt) {
            dmMessageLatency.add(Date.now() - sentAt);
            delete sentAtByContent[directMessage.content];
            receivedCount += 1;
            dmMessagesReceived.add(1);
          }

          if (receivedCount === messageCount) {
            // 5. 전체 메시지 수신 완료 후 idle 유지와 정상 종료 예약
            scheduleIdleClose();
          }

          return;
        }

        if (frame.command === 'ERROR') {
          protocolError =
            frame.body || frame.headers.message || 'STOMP ERROR 프레임 수신';
          dmProtocolErrors.add(1);
          closeGracefully();
        }
      };

      socket.on('message', (payload) => {
        try {
          const packet = parseSockJsPacket(payload);

          if (packet.type === 'open') {
            // SockJS 연결 개방 후 JWT를 포함한 STOMP CONNECT 전송
            sendStomp(socket, 'CONNECT', {
              'accept-version': '1.2',
              host: stompHost,
              'heart-beat': '0,0',
              Authorization: `Bearer ${accessToken}`,
            });
            return;
          }

          if (packet.type === 'messages') {
            // SockJS 메시지 배열 내부의 STOMP 프레임 순차 처리
            packet.messages.forEach(handleStompFrame);
            return;
          }

          if (packet.type === 'close') {
            // 클라이언트 정상 종료가 아닌 서버 종료만 오류로 집계
            if (!closeStarted) {
              protocolError =
                `SockJS 연결 종료: ${JSON.stringify(packet.close)}`;
              dmProtocolErrors.add(1);
            }

            socket.close();
          }
        } catch (error) {
          protocolError = `SockJS/STOMP 처리 실패: ${error.message}`;
          dmProtocolErrors.add(1);
          closeGracefully();
        }
      });

      socket.on('error', (error) => {
        if (closeStarted) {
          return;
        }

        protocolError = `WebSocket 오류: ${error.error()}`;
        dmProtocolErrors.add(1);
      });

      const hardTimeoutSeconds =
        messageCount * 5 +
        receiveTimeoutSeconds +
        idleSeconds +
        10;

      // 타이머 누락과 무응답 연결의 영구 대기 방지
      socket.setTimeout(() => {
        if (!closeStarted) {
          receiveTimedOut = receivedCount < messageCount;
          closeGracefully();
        }
      }, hardTimeoutSeconds * 1000);
    },
  );

  const upgraded = response && response.status === 101;
  const exchangeSucceeded =
    upgraded &&
    stompConnected &&
    !receiveTimedOut &&
    !protocolError &&
    receivedCount === messageCount;

  // WebSocket 연결부터 전체 메시지 수신까지 최종 결과 집계
  check(response, {
    'SockJS WebSocket 연결 성공': () => upgraded,
    'STOMP CONNECT 성공': () => stompConnected,
    'DM 전체 메시지 수신 성공': () => receivedCount === messageCount,
    'DM 프로토콜 오류 없음': () => protocolError === null,
  });
  dmConnectionSuccess.add(upgraded && stompConnected);
  dmMessageExchangeSuccess.add(exchangeSucceeded);

  if (protocolError) {
    console.error(protocolError);
  }
}

export default dmWebSocketScenario;

import {
  dmWebSocketScenario,
  prepareDm,
} from '../user-journey/dm-websocket.js';
import {
  followScenario,
  prepareFollow,
} from '../user-journey/follow.js';
import {
  notificationSseScenario,
  prepareNotification,
} from '../user-journey/notification-sse.js';

// 팔로우, DM, 알림 흐름의 3 VU 동시 smoke 구성
export const options = {
  scenarios: {
    follow_smoke: {
      executor: 'per-vu-iterations',
      exec: 'followSmoke',
      vus: 1,
      iterations: 1,
      maxDuration: '30s',
    },
    dm_smoke: {
      executor: 'per-vu-iterations',
      exec: 'dmSmoke',
      vus: 1,
      iterations: 1,
      maxDuration: '2m',
    },
    notification_smoke: {
      executor: 'per-vu-iterations',
      exec: 'notificationSmoke',
      vus: 1,
      iterations: 1,
      maxDuration: '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    follow_flow_success: ['rate==1'],
    dm_connection_success: ['rate==1'],
    dm_message_exchange_success: ['rate==1'],
    sse_connection_success: ['rate==1'],
    sse_notification_received: ['rate==1'],
    notification_flow_success: ['rate==1'],
  },
};

// 알림 이벤트 발생자 로그인 데이터의 smoke 범위 제한
export function setup() {
  return {
    follow: prepareFollow(3, 0),
    dm: prepareDm(3, 10),
    notification: prepareNotification(3, 3, 20),
  };
}

export function followSmoke(data) {
  // 1. 팔로우 생성부터 해제까지의 전체 흐름
  followScenario(data.follow);
}

export function dmSmoke(data) {
  // 2. DM WebSocket 연결과 메시지 송수신 흐름
  dmWebSocketScenario(data.dm);
}

export function notificationSmoke(data) {
  // 3. SSE 연결과 알림 수신 및 읽음 처리 흐름
  notificationSseScenario(data.notification);
}

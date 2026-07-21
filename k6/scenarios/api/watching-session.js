import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { sleep } from 'k6';
import ws from 'k6/ws';

import { loginAll, headersFromSetup } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';

const WS_URL = (__ENV.WS_URL || 'ws://localhost:8080') + '/ws';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

export const options = {
  stages: [
    { duration: '30s', target: 25 },    // Warm-up
    { duration: '1m',  target: 50 },    // Normal
    { duration: '2m',  target: 70 },    // Peak
    { duration: '1m',  target: 70 },    // Sustain: 최대 부하 유지
    { duration: '30s', target: 0 },     // Cool-down
  ],
  thresholds: {
    ws_connecting: ['p(95)<2000'],
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

// STOMP 프레임 생성 헬퍼
function stompFrame(command, headers, body) {
  let frame = command + '\n';
  for (const [key, value] of Object.entries(headers || {})) {
    frame += `${key}:${value}\n`;
  }
  frame += '\n';
  if (body) frame += body;
  frame += '\0';
  return frame;
}

export function setup() {
  return { tokens: loginAll(users) };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const accessToken = token.accessToken;
  const headers = {
    ...headersFromSetup(token),
    'Content-Type': 'application/json',
  };

  // 시청 세션 테스트할 콘텐츠 ID 확보를 위해 목록 조회
  const listRes = http.get(
    `${BASE_URL}/api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
    {
      headers,
      tags: { endpoint: 'content_list' },
    }
  );

  check(listRes, {
    '콘텐츠 목록 조회 성공': (r) => r.status === 200,
  });

  if (listRes.status !== 200) {
    sleep(1);
    return;
  }

  const contents = listRes.json().content || listRes.json().data || [];
  if (contents.length === 0) {
    sleep(1);
    return;
  }

  // 첫 페이지에서 랜덤 콘텐츠 선택
  const content = contents[Math.floor(Math.random() * contents.length)];
  const contentId = content.id || content.contentId;
  const subscriptionId = `sub-${__VU}-${__ITER}`;

  // SockJS 환경에서 raw WebSocket 연결 시 /ws/websocket 경로 사용
  const wsUrl = `${WS_URL}/websocket`;

  const res = ws.connect(wsUrl, {}, function (socket) {
    socket.on('open', function () {
      // STOMP CONNECT 프레임에 Authorization 헤더로 JWT 전달
      // (서버의 StompAuthChannelInterceptor가 네이티브 헤더에서 Bearer 토큰 추출)
      socket.send(
        stompFrame('CONNECT', {
          'accept-version': '1.2',
          host: 'localhost',
          Authorization: `Bearer ${accessToken}`,
        })
      );
    });

    socket.on('message', function (msg) {
      // CONNECTED 프레임 수신 후 시청 채널 구독
      if (msg.startsWith('CONNECTED')) {
        socket.send(
          stompFrame('SUBSCRIBE', {
            id: subscriptionId,
            destination: `/sub/contents/${contentId}/watch`,
          })
        );

        // 10초 시청 후 구독 해제 및 연결 종료
        socket.setTimeout(function () {
          socket.send(
            stompFrame('UNSUBSCRIBE', {
              id: subscriptionId,
            })
          );

          socket.send(stompFrame('DISCONNECT', { receipt: 'logout' }));
        }, 10000);
      }

      // RECEIPT 프레임 수신 시 소켓 종료 (연결 해제 확인)
      if (msg.startsWith('RECEIPT')) {
        socket.close();
      }
    });

    socket.on('error', function (e) {
      console.error(`WS error (VU ${__VU}): ${e.error()}`);
    });

    // 안전 타임아웃: 15초 후에도 열려있으면 강제 종료
    socket.setTimeout(function () {
      socket.close();
    }, 15000);
  });

  check(res, {
    'WebSocket 연결 성공': (r) => r && r.status === 101,
  });

  sleep(1);
}

import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { sleep } from 'k6';
import ws from 'k6/ws';

import { login, authHeaders } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';

const WS_URL = (__ENV.WS_URL || 'ws://localhost:8080') + '/ws';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '1m', target: 20 },
    { duration: '2m', target: 50 },
    { duration: '1m', target: 0 },
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

export function setup() {}

export default function () {
  // VU별 고유 유저 배정
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = {
    ...authHeaders(accessToken),
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

  // 인증 토큰으로 WebSocket 연결
  const wsUrl = `${WS_URL}?token=${accessToken}`;

  const res = ws.connect(wsUrl, {}, function (socket) {
    socket.on('open', function () {
      // STOMP 프로토콜 연결
      socket.send(
        stompFrame('CONNECT', {
          'accept-version': '1.2',
          host: 'localhost',
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

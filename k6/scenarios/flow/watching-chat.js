import { check } from 'k6';
import http from 'k6/http';
import ws from 'k6/ws';
import { SharedArray } from 'k6/data';

import { login } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';
import { randomItem, randomIntBetween } from '../../shared/helpers.js';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

const WS_URL = (__ENV.WS_URL || 'ws://localhost:8080') + '/ws';

const CHAT_MESSAGES = [
  '이 장면 진짜 좋다',
  'ㅋㅋㅋㅋㅋ',
  '와 대박',
  '여기 반전이야?!',
  '배우 연기 미쳤다',
  '소름 돋네요',
  '이거 명작이다',
];

export const options = {
  scenarios: {
    watching_chat: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 15 },
        { duration: '2m', target: 30 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    ws_connecting: ['p(95)<2000'],
  },
};

function stompFrame(command, headers, body) {
  let frame = command + '\n';
  for (const [key, value] of Object.entries(headers || {})) {
    frame += `${key}:${value}\n`;
  }
  frame += '\n';
  if (body) {
    frame += body;
  }
  frame += '\0';
  return frame;
}

export default function () {
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);

  // 1. 시청할 콘텐츠 선택
  const listRes = http.get(
    `${BASE_URL}/api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` } }
  );

  const contentList = JSON.parse(listRes.body);
  if (!contentList.data || contentList.data.length === 0) {
    console.warn('콘텐츠가 없어 시청 세션 시나리오 스킵');
    return;
  }

  const contentId = randomItem(contentList.data).id;
  const watchDestination = `/sub/contents/${contentId}/watch`;
  const chatDestination = `/sub/contents/${contentId}/chat`;
  const chatSendDestination = `/pub/contents/${contentId}/chat`;

  let joinReceived = false;
  let chatReceived = 0;

  // 2. WebSocket + STOMP 시청 세션 + 채팅
  // SockJS 환경에서 raw WebSocket 연결 시 /ws/websocket 경로 사용
  const wsRes = ws.connect(`${WS_URL}/websocket`, {}, function (socket) {
    // STOMP CONNECT 프레임에 Authorization 헤더로 JWT 전달
    socket.send(stompFrame('CONNECT', {
      'accept-version': '1.2',
      'heart-beat': '0,0',
      Authorization: `Bearer ${accessToken}`,
    }));

    socket.on('message', function (msg) {
      if (msg.startsWith('CONNECTED')) {
        socket.send(stompFrame('SUBSCRIBE', {
          id: 'sub-watch',
          destination: watchDestination,
        }));
        socket.send(stompFrame('SUBSCRIBE', {
          id: 'sub-chat',
          destination: chatDestination,
        }));
      }

      if (msg.includes('"type":"JOIN"')) {
        joinReceived = true;
      }

      if (msg.includes('"content"') && msg.includes('"sender"')) {
        chatReceived++;
      }
    });

    socket.on('error', function (e) {
      console.error(`WebSocket 에러: ${e.error()}`);
    });

    // 5초 후 시청 세션 목록 REST 조회
    socket.setTimeout(function () {
      http.get(
        `${BASE_URL}/api/contents/${contentId}/watching-sessions?sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
        {
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
          tags: { name: 'watching_session_list' },
        }
      );
    }, 5000);

    // 채팅 3~4마디 (8초부터 5초 간격)
    const chatCount = randomIntBetween(3, 4);
    for (let i = 0; i < chatCount; i++) {
      socket.setTimeout(function () {
        socket.send(stompFrame('SEND', {
          destination: chatSendDestination,
          'content-type': 'application/json',
        }, JSON.stringify({ content: randomItem(CHAT_MESSAGES) })));
      }, 8000 + i * 5000);
    }

    // 30초 시청 후 퇴장
    socket.setTimeout(function () {
      socket.send(stompFrame('UNSUBSCRIBE', { id: 'sub-chat' }));
      socket.send(stompFrame('UNSUBSCRIBE', { id: 'sub-watch' }));

      socket.setTimeout(function () {
        socket.send(stompFrame('DISCONNECT', { receipt: 'disconnect-receipt' }));
        socket.close();
      }, 2000);
    }, 30000);
  });

  check(wsRes, {
    'WebSocket 연결 성공': (r) => r && r.status === 101,
  });

  check(null, {
    'JOIN 이벤트 수신': () => joinReceived,
    '채팅 메시지 수신': () => chatReceived > 0,
  });
}

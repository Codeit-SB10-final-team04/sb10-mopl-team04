import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { sleep } from 'k6';

import { loginAll, headersFromSetup } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';

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
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  return { tokens: loginAll(users) };
}

export default function (data) {
  const user = users[(__VU - 1) % users.length];
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const headers = {
    ...headersFromSetup(token),
    'Content-Type': 'application/json',
  };

  // 구독할 플레이리스트 선택을 위해 목록 조회
  const listRes = http.get(
    `${BASE_URL}/api/playlists?sortBy=subscribeCount&sortDirection=DESCENDING&limit=20`,
    {
      headers,
      tags: { endpoint: 'playlist_list' },
    }
  );

  check(listRes, {
    '플레이리스트 목록 조회 성공': (r) => r.status === 200,
  });

  if (listRes.status !== 200) {
    sleep(1);
    return;
  }

  const playlists = listRes.json().content || listRes.json().data || [];

  // 본인 플레이리스트 제외 (자기 구독 방지)
  // API 응답의 owner는 중첩 객체이므로 owner.email 또는 owner.userId로 비교
  const otherPlaylists = playlists.filter(
    (p) => {
      const ownerEmail = (p.owner && p.owner.email) || p.ownerEmail;
      return ownerEmail !== user.email;
    }
  );

  if (otherPlaylists.length === 0) {
    sleep(1);
    return;
  }

  const playlist = otherPlaylists[Math.floor(Math.random() * otherPlaylists.length)];
  const playlistId = playlist.id || playlist.playlistId;

  // 플레이리스트 구독
  const subRes = http.post(
    `${BASE_URL}/api/playlists/${playlistId}/subscription`,
    null,
    {
      headers,
      tags: { endpoint: 'playlist_subscribe' },
    }
  );

  check(subRes, {
    '구독 성공': (r) => r.status === 204,
  });

  // 데이터 정리: 구독 취소
  const unsubRes = http.del(
    `${BASE_URL}/api/playlists/${playlistId}/subscription`,
    null,
    {
      headers,
      tags: { endpoint: 'playlist_unsubscribe' },
    }
  );

  check(unsubRes, {
    '구독 취소 성공': (r) => r.status === 204,
  });

  sleep(1);
}

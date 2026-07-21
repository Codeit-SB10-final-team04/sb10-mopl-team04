import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { sleep } from 'k6';

import { login, authHeaders } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';

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
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {}

export default function () {
  // VU별 고유 유저 배정
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = {
    ...authHeaders(accessToken),
    'Content-Type': 'application/json',
  };

  // 플레이리스트 목록 조회 (상세 조회할 ID 확보용)
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
  if (playlists.length === 0) {
    sleep(1);
    return;
  }

  // 첫 페이지에서 랜덤 플레이리스트 선택 후 상세 조회
  const playlist = playlists[Math.floor(Math.random() * playlists.length)];
  const playlistId = playlist.id || playlist.playlistId;

  const detailRes = http.get(`${BASE_URL}/api/playlists/${playlistId}`, {
    headers,
    tags: { endpoint: 'playlist_detail' },
  });

  check(detailRes, {
    '플레이리스트 상세 조회 성공': (r) => r.status === 200,
  });

  sleep(1);
}

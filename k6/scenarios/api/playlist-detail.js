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
    'http_req_duration{endpoint:playlist_detail}': ['p(95)<300'],
    'http_req_duration{endpoint:playlist_list}': ['p(95)<500'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  return { tokens: loginAll(users) };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const headers = {
    ...headersFromSetup(token),
    'Content-Type': 'application/json',
  };

  // 플레이리스트 목록 커서 기반 페이지네이션 (최대 3페이지, 상세 조회할 ID 확보용)
  let cursor = null;
  let allPlaylists = [];

  for (let page = 0; page < 3; page++) {
    let url = `${BASE_URL}/api/playlists?sortBy=subscribeCount&sortDirection=DESCENDING&limit=20`;
    if (cursor) {
      url += `&cursor=${encodeURIComponent(cursor)}`;
    }

    const listRes = http.get(url, {
      headers,
      tags: { endpoint: 'playlist_list' },
    });

    check(listRes, {
      '플레이리스트 목록 조회 성공': (r) => r.status === 200,
    });

    if (listRes.status !== 200) break;

    // 다음 페이지 커서 추출
    const body = listRes.json();
    const items = body.data || [];
    allPlaylists = allPlaylists.concat(items);

    const hasNext = body.hasNext;
    cursor = body.cursor || null;

    if (!hasNext) break;
  }

  if (allPlaylists.length === 0) {
    sleep(1);
    return;
  }

  // 수집한 플레이리스트에서 랜덤 선택 후 상세 조회
  const playlist = allPlaylists[Math.floor(Math.random() * allPlaylists.length)];
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

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

export function setup() {}

export default function () {
  // VU별 고유 유저 배정
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = {
    ...authHeaders(accessToken),
    'Content-Type': 'application/json',
  };

  // 플레이리스트 목록 커서 기반 페이지네이션 (최대 3페이지)
  let cursor = null;

  for (let page = 0; page < 3; page++) {
    let url = `${BASE_URL}/api/playlists?sortBy=subscribeCount&sortDirection=DESCENDING&limit=20`;
    if (cursor) {
      url += `&cursor=${cursor}`;
    }

    const res = http.get(url, {
      headers,
      tags: { endpoint: 'playlist_list' },
    });

    check(res, {
      '플레이리스트 목록 조회 성공': (r) => r.status === 200,
    });

    if (res.status !== 200) break;

    // 다음 페이지 커서 추출
    const body = res.json();
    const hasNext = body.hasNext;
    cursor = body.cursor || null;

    if (!hasNext) break;
  }

  sleep(1);
}

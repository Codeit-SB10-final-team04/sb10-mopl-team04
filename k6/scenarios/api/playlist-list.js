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
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {}

export default function () {
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = {
    ...authHeaders(accessToken),
    'Content-Type': 'application/json',
  };

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

    const body = res.json();
    const hasNext = body.hasNext;
    cursor = body.cursor || null;

    if (!hasNext) break;
  }

  sleep(1);
}

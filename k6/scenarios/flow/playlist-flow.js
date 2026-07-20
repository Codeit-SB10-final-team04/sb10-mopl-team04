import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

import { login, authHeaders } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';
import { randomItem, thinkTime } from '../../shared/helpers.js';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

export const options = {
  scenarios: {
    playlist_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 20 },
        { duration: '2m', target: 50 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:playlist_detail}': ['p(95)<500'],
    'http_req_duration{name:subscribe}': ['p(95)<800'],
  },
};

export default function () {
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = { ...authHeaders(accessToken), 'Content-Type': 'application/json' };

  // 1. 플레이리스트 목록 조회 (구독순)
  const listRes = http.get(
    `${BASE_URL}/api/playlists?sortBy=subscribeCount&sortDirection=DESCENDING&limit=20`,
    { headers, tags: { name: 'playlist_list' } }
  );

  check(listRes, { '플레이리스트 목록 200': (r) => r.status === 200 });

  const playlists = JSON.parse(listRes.body).data || [];
  if (playlists.length === 0) {
    return;
  }

  thinkTime(1, 2);

  // 2. 플레이리스트 상세 조회
  const playlist = randomItem(playlists.slice(0, 5));
  const detailRes = http.get(
    `${BASE_URL}/api/playlists/${playlist.id}`,
    { headers, tags: { name: 'playlist_detail' } }
  );

  check(detailRes, { '플레이리스트 상세 200': (r) => r.status === 200 });

  thinkTime(2, 3);

  // 3. 다른 유저의 플레이리스트 구독
  const targets = playlists.filter((p) => p.owner.userId !== user.id);
  if (targets.length === 0) {
    return;
  }

  const target = randomItem(targets.slice(0, 3));

  const subRes = http.post(
    `${BASE_URL}/api/playlists/${target.id}/subscription`,
    null,
    { headers, tags: { name: 'subscribe' } }
  );

  check(subRes, { '구독 204': (r) => r.status === 204 });

  thinkTime(2, 3);

  // 4. 구독 취소 (데이터 정리)
  const unsubRes = http.del(
    `${BASE_URL}/api/playlists/${target.id}/subscription`,
    null,
    { headers, tags: { name: 'unsubscribe' } }
  );

  check(unsubRes, { '구독 취소 204': (r) => r.status === 204 });
}

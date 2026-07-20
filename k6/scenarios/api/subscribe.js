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

  // Fetch playlist list
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

  // Filter out own playlists
  const otherPlaylists = playlists.filter(
    (p) => p.ownerEmail !== user.email && p.creatorEmail !== user.email
  );

  if (otherPlaylists.length === 0) {
    sleep(1);
    return;
  }

  const playlist = otherPlaylists[Math.floor(Math.random() * otherPlaylists.length)];
  const playlistId = playlist.id || playlist.playlistId;

  // Subscribe
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

  // Cleanup: Unsubscribe
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

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
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = authHeaders(accessToken);

  // Fetch content list to get IDs
  const listRes = http.get(
    `${BASE_URL}/api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
    { headers, tags: { endpoint: 'content_list_for_detail' } },
  );

  check(listRes, {
    '콘텐츠 목록 조회 성공': (r) => r.status === 200,
  });

  if (listRes.status !== 200) {
    sleep(1);
    return;
  }

  const contents = listRes.json().contents || listRes.json().content || [];
  if (contents.length === 0) {
    sleep(1);
    return;
  }

  // Pick a random content from first page
  const randomContent = contents[Math.floor(Math.random() * contents.length)];
  const contentId = randomContent.id || randomContent.contentId;

  const detailRes = http.get(`${BASE_URL}/api/contents/${contentId}`, {
    headers,
    tags: { endpoint: 'content_detail' },
  });

  check(detailRes, {
    '콘텐츠 상세 조회 성공': (r) => r.status === 200,
  });

  sleep(1);
}

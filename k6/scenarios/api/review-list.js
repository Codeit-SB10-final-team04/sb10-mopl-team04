import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

import { loginAll, headersFromSetup } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

export const options = {
  scenarios: {
    review_list: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 25 },    // Warm-up
        { duration: '1m',  target: 50 },    // Normal
        { duration: '2m',  target: 70 },    // Peak
        { duration: '1m',  target: 70 },    // Sustain: 최대 부하 유지
        { duration: '30s', target: 0 },     // Cool-down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  return { tokens: loginAll(users) };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const headers = { 'Content-Type': 'application/json', ...headersFromSetup(token) };

  // 콘텐츠 목록에서 랜덤 선택
  const listRes = http.get(
    `${BASE_URL}/api/contents?sortBy=reviewCount&sortDirection=DESCENDING&limit=20`,
    { headers }
  );

  if (listRes.status !== 200) {
    sleep(1);
    return;
  }

  const contents = JSON.parse(listRes.body).data || [];
  if (contents.length === 0) {
    sleep(1);
    return;
  }

  const contentId = contents[Math.floor(Math.random() * contents.length)].id;

  // 리뷰 목록 페이지네이션 (최대 3페이지)
  let cursor = null;
  let idAfter = null;

  for (let page = 0; page < 3; page++) {
    let url = `${BASE_URL}/api/reviews?contentId=${contentId}&sortBy=createdAt&sortDirection=DESCENDING&limit=20`;
    if (cursor && idAfter) {
      url += `&cursor=${encodeURIComponent(cursor)}&idAfter=${idAfter}`;
    }

    const res = http.get(url, { headers, tags: { name: 'review_list' } });

    check(res, {
      '리뷰 목록 200': (r) => r.status === 200,
    });

    if (res.status !== 200) break;

    const body = JSON.parse(res.body);
    if (!body.hasNext || !body.nextCursor || !body.nextIdAfter) break;

    cursor = body.nextCursor;
    idAfter = body.nextIdAfter;
  }

  sleep(1);
}

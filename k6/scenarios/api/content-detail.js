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
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {}

export default function () {
  // VU별 고유 유저 배정
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = authHeaders(accessToken);

  // 콘텐츠 목록 커서 기반 페이지네이션 (최대 3페이지, 상세 조회할 ID 확보용)
  let cursor = null;
  let idAfter = null;
  let allContents = [];

  for (let page = 0; page < 3; page++) {
    let url = `${BASE_URL}/api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=20`;
    if (cursor && idAfter) {
      url += `&cursor=${cursor}&idAfter=${idAfter}`;
    }

    const listRes = http.get(url, {
      headers,
      tags: { endpoint: 'content_list_for_detail' },
    });

    check(listRes, {
      '콘텐츠 목록 조회 성공': (r) => r.status === 200,
    });

    if (listRes.status !== 200) break;

    // 다음 페이지 커서 추출
    const body = listRes.json();
    const items = body.data || [];
    allContents = allContents.concat(items);

    const hasNext = body.hasNext;
    cursor = body.nextCursor || null;
    idAfter = body.nextIdAfter || null;

    if (!hasNext) break;
  }

  if (allContents.length === 0) {
    sleep(1);
    return;
  }

  // 수집한 콘텐츠에서 랜덤 선택 후 상세 조회
  const randomContent = allContents[Math.floor(Math.random() * allContents.length)];
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

import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { sleep } from 'k6';

import { loginAll, headersFromSetup } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

const keywords = [
  '인터스텔라',
  '어벤져스',
  '기생충',
  '올드보이',
  '부산행',
  '괴물',
  '범죄도시',
];

export const options = {
  stages: [
    { duration: '30s', target: 25 },    // Warm-up
    { duration: '1m',  target: 50 },    // Normal
    { duration: '2m',  target: 70 },    // Peak
    { duration: '1m',  target: 70 },    // Sustain: 최대 부하 유지
    { duration: '30s', target: 0 },     // Cool-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  return { tokens: loginAll(users) };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const headers = headersFromSetup(token);

  // 랜덤 키워드로 콘텐츠 검색 커서 기반 페이지네이션 (최대 3페이지)
  const keyword = keywords[Math.floor(Math.random() * keywords.length)];
  let cursor = null;
  let idAfter = null;

  for (let page = 0; page < 3; page++) {
    let url = `${BASE_URL}/api/contents?keywordLike=${encodeURIComponent(keyword)}&limit=20`;
    if (cursor && idAfter) {
      url += `&cursor=${encodeURIComponent(cursor)}&idAfter=${idAfter}`;
    }

    const res = http.get(url, {
      headers,
      tags: { endpoint: 'content_search' },
    });

    check(res, {
      '콘텐츠 검색 성공': (r) => r.status === 200,
    });

    if (res.status !== 200) break;

    // 다음 페이지 커서 추출
    const body = res.json();
    const hasNext = body.hasNext;
    cursor = body.nextCursor || null;
    idAfter = body.nextIdAfter || null;

    if (!hasNext) break;
  }

  sleep(1);
}

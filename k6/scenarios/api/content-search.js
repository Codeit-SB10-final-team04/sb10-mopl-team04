import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { sleep } from 'k6';

import { login, authHeaders } from '../../shared/auth.js';
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
    { duration: '30s', target: 5 },
    { duration: '1m', target: 20 },
    { duration: '2m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {}

export default function () {
  // VU별 고유 유저 배정
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = authHeaders(accessToken);

  // 랜덤 키워드로 콘텐츠 검색
  const keyword = keywords[Math.floor(Math.random() * keywords.length)];

  const res = http.get(
    `${BASE_URL}/api/contents?keywordLike=${encodeURIComponent(keyword)}&limit=20`,
    { headers, tags: { endpoint: 'content_search' } },
  );

  check(res, {
    '콘텐츠 검색 성공': (r) => r.status === 200,
  });

  sleep(1);
}

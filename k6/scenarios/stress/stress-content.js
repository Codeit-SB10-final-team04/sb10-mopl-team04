import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

import { login, authHeaders } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';
import { randomItem } from '../../shared/helpers.js';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

const SEARCH_KEYWORDS = ['인터스텔라', '어벤져스', '기생충', '올드보이', '부산행', '괴물', '범죄도시'];

export const options = {
  scenarios: {
    stress_content: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },
        { duration: '3m', target: 500 },
        { duration: '3m', target: 1000 },
        { duration: '2m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    http_req_failed: ['rate<0.10'],
    'http_req_duration{name:content_list}': ['p(95)<2000'],
    'http_req_duration{name:content_search}': ['p(95)<3000'],
    'http_req_duration{name:content_detail}': ['p(95)<1000'],
  },
};

export default function () {
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` };

  // 콘텐츠 목록 조회
  const listRes = http.get(
    `${BASE_URL}/api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
    { headers, tags: { name: 'content_list' } }
  );
  check(listRes, { '목록 200': (r) => r.status === 200 });

  // 키워드 검색
  const keyword = randomItem(SEARCH_KEYWORDS);
  const searchRes = http.get(
    `${BASE_URL}/api/contents?keywordLike=${encodeURIComponent(keyword)}&limit=20`,
    { headers, tags: { name: 'content_search' } }
  );
  check(searchRes, { '검색 200': (r) => r.status === 200 });

  // 상세 조회
  const contents = JSON.parse(listRes.body).data || [];
  if (contents.length > 0) {
    const content = randomItem(contents);
    const detailRes = http.get(
      `${BASE_URL}/api/contents/${content.id}`,
      { headers, tags: { name: 'content_detail' } }
    );
    check(detailRes, { '상세 200': (r) => r.status === 200 });
  }

  sleep(1);
}

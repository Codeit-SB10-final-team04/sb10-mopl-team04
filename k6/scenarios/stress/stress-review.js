import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

import { login, authHeaders } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';
import { randomIntBetween, randomItem } from '../../shared/helpers.js';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

const REVIEW_TEXTS = [
  '부하 테스트 리뷰 - 좋아요',
  '부하 테스트 리뷰 - 보통이에요',
  '부하 테스트 리뷰 - 별로예요',
];

export const options = {
  scenarios: {
    stress_review: {
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
    'http_req_duration{name:review_list}': ['p(95)<2000'],
    'http_req_duration{name:review_create}': ['p(95)<3000'],
  },
};

export default function () {
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = { ...authHeaders(accessToken), 'Content-Type': 'application/json' };

  // 콘텐츠 목록에서 랜덤 선택
  const listRes = http.get(
    `${BASE_URL}/api/contents?sortBy=reviewCount&sortDirection=DESCENDING&limit=20`,
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` } }
  );

  const contents = JSON.parse(listRes.body).data || [];
  if (contents.length === 0) {
    return;
  }

  const contentId = randomItem(contents).id;

  // 리뷰 목록 읽기
  const reviewListRes = http.get(
    `${BASE_URL}/api/reviews?contentId=${contentId}&sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` }, tags: { name: 'review_list' } }
  );
  check(reviewListRes, { '리뷰 목록 200': (r) => r.status === 200 });

  // 리뷰 작성 + 삭제 (쓰기 경합 테스트)
  const createRes = http.post(
    `${BASE_URL}/api/reviews`,
    JSON.stringify({
      contentId,
      text: randomItem(REVIEW_TEXTS),
      rating: randomIntBetween(1, 5),
    }),
    { headers, tags: { name: 'review_create' } }
  );
  check(createRes, { '리뷰 작성 성공': (r) => r.status === 200 });

  if (createRes.status === 200) {
    const reviewId = JSON.parse(createRes.body).id;
    http.del(`${BASE_URL}/api/reviews/${reviewId}`, null, { headers, tags: { name: 'review_delete' } });
  }

  sleep(1);
}

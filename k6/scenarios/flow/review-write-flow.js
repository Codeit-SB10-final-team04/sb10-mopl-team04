import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

import { login, authHeaders } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';
import { randomIntBetween, randomItem, thinkTime } from '../../shared/helpers.js';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

const REVIEW_TEXTS = [
  '정말 재밌었어요! 강추합니다.',
  '기대보다 별로였어요.',
  '배우들 연기가 좋았습니다.',
  '스토리가 탄탄해서 몰입감 있었어요.',
  '시간 가는 줄 몰랐습니다.',
  '음악이 너무 좋았어요.',
  '조금 지루한 부분도 있었지만 전반적으로 괜찮았어요.',
];

export const options = {
  scenarios: {
    review_write_flow: {
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
    'http_req_duration{name:review_create}': ['p(95)<1000'],
  },
};

export default function () {
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = { ...authHeaders(accessToken), 'Content-Type': 'application/json' };
  const readHeaders = { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` };

  // 1. 콘텐츠 목록 조회 (인기순)
  const listRes = http.get(
    `${BASE_URL}/api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
    { headers: readHeaders, tags: { name: 'content_list' } }
  );

  check(listRes, { '콘텐츠 목록 200': (r) => r.status === 200 });

  const contentList = JSON.parse(listRes.body);
  if (!contentList.data || contentList.data.length === 0) {
    return;
  }

  thinkTime(1, 2);

  // 2. 콘텐츠 상세 조회
  const content = randomItem(contentList.data.slice(0, 5));
  const detailRes = http.get(
    `${BASE_URL}/api/contents/${content.id}`,
    { headers: readHeaders, tags: { name: 'content_detail' } }
  );

  check(detailRes, { '콘텐츠 상세 200': (r) => r.status === 200 });

  thinkTime(2, 3);

  // 3. 리뷰 작성
  const createRes = http.post(
    `${BASE_URL}/api/reviews`,
    JSON.stringify({
      contentId: content.id,
      text: randomItem(REVIEW_TEXTS),
      rating: randomIntBetween(1, 5),
    }),
    { headers, tags: { name: 'review_create' } }
  );

  check(createRes, { '리뷰 작성 성공': (r) => r.status === 200 });

  if (createRes.status !== 200) {
    return;
  }

  const reviewId = JSON.parse(createRes.body).id;

  thinkTime(1, 2);

  // 4. 리뷰 삭제 (데이터 정리)
  const deleteRes = http.del(
    `${BASE_URL}/api/reviews/${reviewId}`,
    null,
    { headers, tags: { name: 'review_delete' } }
  );

  check(deleteRes, { '리뷰 삭제 204': (r) => r.status === 204 });
}

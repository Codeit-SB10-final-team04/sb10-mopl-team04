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
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {}

export default function () {
  // VU별 고유 유저 배정
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = {
    ...authHeaders(accessToken),
    'Content-Type': 'application/json',
  };

  // 리뷰 작성할 콘텐츠 선택을 위해 목록 조회
  const listRes = http.get(
    `${BASE_URL}/api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
    { headers, tags: { endpoint: 'content_list_for_review_create' } },
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

  const randomContent = contents[Math.floor(Math.random() * contents.length)];
  const contentId = randomContent.id || randomContent.contentId;
  const randomRating = Math.floor(Math.random() * 5) + 1;

  // 리뷰 생성
  const createRes = http.post(
    `${BASE_URL}/api/reviews`,
    JSON.stringify({
      contentId: contentId,
      text: '부하 테스트 리뷰입니다.',
      rating: randomRating,
    }),
    { headers, tags: { endpoint: 'review_create' } },
  );

  const createSuccess = check(createRes, {
    '리뷰 생성 성공': (r) => r.status === 200 || r.status === 201,
  });

  // 데이터 정리: 생성된 리뷰 삭제
  if (createSuccess) {
    const reviewId =
      createRes.json().id || createRes.json().reviewId;

    if (reviewId) {
      const deleteRes = http.del(`${BASE_URL}/api/reviews/${reviewId}`, null, {
        headers,
        tags: { endpoint: 'review_delete_cleanup' },
      });

      check(deleteRes, {
        '리뷰 삭제(정리) 성공': (r) =>
          r.status === 200 || r.status === 204,
      });
    }
  }

  sleep(1);
}

import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

import { login, authHeaders } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';
import { randomIntBetween, randomItem, thinkTime } from '../../shared/helpers.js';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

const SEARCH_KEYWORDS = ['인터스텔라', '어벤져스', '기생충', '올드보이', '부산행', '괴물', '범죄도시'];

export const options = {
  scenarios: {
    content_browse: {
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
  },
};

export default function () {
  // VU별 고유 유저 배정
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = { ...authHeaders(accessToken), 'Content-Type': 'application/json' };

  //   1. 콘텐츠 목록 조회 (인기순)
  const listRes = http.get(
      `${BASE_URL}/api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
      { headers, tags: { name: 'content_list' } }
  );

  check(listRes, {'콘텐츠 목록 200' : (r) => r.status === 200 } )

  const contents = JSON.parse(listRes.body).data || [];
  if (contents.length === 0) {
    return;
  };

  thinkTime(1, 3);

  //   2. 키워드 검색 (SEARCH_KEYWORDS에서 랜덤)
  const keyword = randomItem(SEARCH_KEYWORDS);

  const keywordRes = http.get(
      `${BASE_URL}/api/contents?keywordLike=${keyword}&limit=20`,
      { headers, tags: { name: 'content_search' } }
  );

  const details = JSON.parse(keywordRes.body).data;
  if (details.length === 0) {
    return;
  }

  check(keywordRes, { '콘텐츠 키워드 검색 200': (r) => r.status === 200} );

  thinkTime(1, 3);

  //   3. 콘텐츠 상세 조회
  const detail = randomItem(details.slice(0, 5));
  const detailRes = http.get(
      `${BASE_URL}/api/contents/${detail.id}`,
      { headers, tags: { name: 'content_detail' } }
  );

  check(detailRes, { '콘텐츠 상세 200': (r) => r.status === 200});

  thinkTime(1, 3);

  //   4. 해당 콘텐츠의 리뷰 목록 조회
  const detailId = detail.id || detail.contentId;

  const reviewRes = http.get(
      `${BASE_URL}/api/reviews?contentId=${detailId}&sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
      { headers, tags: { name: 'review_list' } }
  );

  check(reviewRes, { '리뷰 목록 조회 200': (r) => r.status === 200} );
}

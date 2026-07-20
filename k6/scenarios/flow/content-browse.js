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

  // TODO(human): 콘텐츠 탐색 플로우 구현
  // 유저가 콘텐츠를 탐색하는 실제 사용 패턴을 시뮬레이션하세요.
  //
  // 추천 플로우:
  //   1. 콘텐츠 목록 조회 (인기순)
  //   2. 키워드 검색 (SEARCH_KEYWORDS에서 랜덤)
  //   3. 콘텐츠 상세 조회
  //   4. 해당 콘텐츠의 리뷰 목록 조회
  //
  // 사용 가능한 API:
  //   GET /api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=20
  //   GET /api/contents?keywordLike={keyword}&limit=20
  //   GET /api/contents/{id}
  //   GET /api/reviews?contentId={id}&sortBy=createdAt&sortDirection=DESCENDING&limit=20
  //
  // 힌트:
  //   - thinkTime(1, 3) 으로 단계 사이 대기
  //   - check(res, { '이름': (r) => r.status === 200 }) 으로 응답 검증
  //   - JSON.parse(res.body).data 로 목록 데이터 접근
  //   - headers 변수에 인증 헤더가 이미 들어있음
}

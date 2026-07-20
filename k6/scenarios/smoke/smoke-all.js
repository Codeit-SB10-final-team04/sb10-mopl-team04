import http from 'k6/http';
import { check, fail } from 'k6';
import { SharedArray } from 'k6/data';

import { login, authHeaders } from '../../shared/auth.js';
import { BASE_URL } from '../../shared/config.js';

const users = new SharedArray('users', function () {
  return JSON.parse(open('../../data/users.json')).users;
});

export const options = {
  vus: 3,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate==0'],       // 에러 0건
    http_req_duration: ['p(95)<2000'],  // 넉넉한 기준
  },
};

export default function () {
  const user = users[(__VU - 1) % users.length];
  const accessToken = login(user.email, user.password);
  const headers = { ...authHeaders(accessToken), 'Content-Type': 'application/json' };
  const readHeaders = { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` };

  // 1. 콘텐츠 목록
  const contentListRes = http.get(
    `${BASE_URL}/api/contents?sortBy=watcherCount&sortDirection=DESCENDING&limit=5`,
    { headers: readHeaders, tags: { name: 'content_list' } }
  );
  check(contentListRes, { '콘텐츠 목록 200': (r) => r.status === 200 });

  const contents = JSON.parse(contentListRes.body).data || [];
  if (contents.length === 0) {
    fail('콘텐츠가 없어 smoke 테스트 불가');
  }

  const contentId = contents[0].id;

  // 2. 콘텐츠 상세
  const detailRes = http.get(
    `${BASE_URL}/api/contents/${contentId}`,
    { headers: readHeaders, tags: { name: 'content_detail' } }
  );
  check(detailRes, { '콘텐츠 상세 200': (r) => r.status === 200 });

  // 3. 콘텐츠 검색
  const searchRes = http.get(
    `${BASE_URL}/api/contents?keywordLike=${encodeURIComponent('기생충')}&limit=5`,
    { headers: readHeaders, tags: { name: 'content_search' } }
  );
  check(searchRes, { '콘텐츠 검색 200': (r) => r.status === 200 });

  // 4. 리뷰 목록
  const reviewListRes = http.get(
    `${BASE_URL}/api/reviews?contentId=${contentId}&sortBy=createdAt&sortDirection=DESCENDING&limit=5`,
    { headers: readHeaders, tags: { name: 'review_list' } }
  );
  check(reviewListRes, { '리뷰 목록 200': (r) => r.status === 200 });

  // 5. 리뷰 작성 + 삭제
  const createRes = http.post(
    `${BASE_URL}/api/reviews`,
    JSON.stringify({ contentId, text: 'smoke 테스트 리뷰', rating: 3 }),
    { headers, tags: { name: 'review_create' } }
  );
  check(createRes, { '리뷰 작성 200': (r) => r.status === 200 });

  if (createRes.status === 200) {
    const reviewId = JSON.parse(createRes.body).id;
    const deleteRes = http.del(
      `${BASE_URL}/api/reviews/${reviewId}`,
      null,
      { headers, tags: { name: 'review_delete' } }
    );
    check(deleteRes, { '리뷰 삭제 204': (r) => r.status === 204 });
  }

  // 6. 플레이리스트 목록
  const playlistRes = http.get(
    `${BASE_URL}/api/playlists?sortBy=subscribeCount&sortDirection=DESCENDING&limit=5`,
    { headers, tags: { name: 'playlist_list' } }
  );
  check(playlistRes, { '플레이리스트 목록 200': (r) => r.status === 200 });

  const playlists = JSON.parse(playlistRes.body).data || [];

  // 7. 플레이리스트 상세
  if (playlists.length > 0) {
    const playlistDetailRes = http.get(
      `${BASE_URL}/api/playlists/${playlists[0].id}`,
      { headers, tags: { name: 'playlist_detail' } }
    );
    check(playlistDetailRes, { '플레이리스트 상세 200': (r) => r.status === 200 });

    // 8. 구독 + 취소 (다른 유저 플레이리스트만)
    const otherPlaylist = playlists.find((p) => p.owner.userId !== user.id);
    if (otherPlaylist) {
      const subRes = http.post(
        `${BASE_URL}/api/playlists/${otherPlaylist.id}/subscription`,
        null,
        { headers, tags: { name: 'subscribe' } }
      );
      check(subRes, { '구독 204': (r) => r.status === 204 });

      const unsubRes = http.del(
        `${BASE_URL}/api/playlists/${otherPlaylist.id}/subscription`,
        null,
        { headers, tags: { name: 'unsubscribe' } }
      );
      check(unsubRes, { '구독 취소 204': (r) => r.status === 204 });
    }
  }
}

import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

/**
 * users.json의 테스트 계정으로 로그인 후 플레이리스트 생성 + 콘텐츠 추가 + 구독하는 시딩 스크립트
 *
 * 실행 예시:
 *   node k6/scripts/seed-playlists.mjs
 *
 * 환경 변수:
 *   BASE_URL               대상 서버 주소 (기본값: http://localhost:8080)
 *   PLAYLISTS_PER_USER     유저당 플레이리스트 수 (기본값: 3, 최대 10)
 *   CONTENTS_PER_PLAYLIST  플레이리스트당 콘텐츠 수 (기본값: 5, 최대 20)
 *   SUBSCRIPTIONS_PER_USER 유저당 구독 수 (기본값: 5, 최대 20)
 *   SEED_CONCURRENCY       동시 처리 유저 수 (기본값: 5)
 *   ALLOW_REMOTE_SEED      원격 서버 시딩 허용 (기본값: false)
 */

const REQUEST_TIMEOUT_MS = 10_000;
const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';

const baseUrl = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const playlistsPerUser = Math.min(
  Number.parseInt(process.env.PLAYLISTS_PER_USER || '3', 10),
  10,
);
const contentsPerPlaylist = Math.min(
  Number.parseInt(process.env.CONTENTS_PER_PLAYLIST || '5', 10),
  20,
);
const subscriptionsPerUser = Math.min(
  Number.parseInt(process.env.SUBSCRIPTIONS_PER_USER || '5', 10),
  20,
);
const concurrency = Number.parseInt(process.env.SEED_CONCURRENCY || '5', 10);
const allowRemoteSeed = (process.env.ALLOW_REMOTE_SEED || 'false').toLowerCase() === 'true';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const usersPath = path.resolve(scriptDirectory, '../data/users.json');

const PLAYLIST_TITLES = [
  '주말에 보기 좋은 영화',
  '꿀잠 전에 볼 콘텐츠',
  '인생 명작 모음',
  '가볍게 보는 코미디',
  '몰입감 최고 스릴러',
  '감동 실화 모음',
  '비 오는 날 보기 좋은',
  'SF 좋아하시면 이거',
  '완결작만 모았다',
  '친구 추천 리스트',
  '다시 보고 싶은 작품',
  '올해 베스트 모음',
];

const PLAYLIST_DESCRIPTIONS = [
  '개인적으로 좋았던 작품들을 모아봤어요.',
  '시간 날 때 하나씩 보기 좋은 리스트입니다.',
  '평점 높은 것 위주로 정리했어요.',
  '분위기별로 골라 봤습니다.',
  '추천받은 것들 정리해둔 리스트예요.',
];

const isLocalTarget = () => {
  const targetUrl = new URL(baseUrl);
  return new Set(['localhost', '127.0.0.1', '::1', '[::1]']).has(targetUrl.hostname);
};

const validateConfiguration = () => {
  if (!isLocalTarget() && !allowRemoteSeed) {
    throw new Error('원격 서버 시딩이 차단됐습니다. ALLOW_REMOTE_SEED=true를 명시해야 합니다.');
  }
  if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 20) {
    throw new Error('SEED_CONCURRENCY는 1 이상 20 이하여야 합니다.');
  }
};

const fetchWithTimeout = async (url, options = {}) => {
  try {
    return await fetch(url, {
      ...options,
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (error) {
    if (error?.name === 'TimeoutError' || error?.name === 'AbortError') {
      throw new Error(`HTTP 요청 시간이 ${REQUEST_TIMEOUT_MS / 1000}초를 초과했습니다: ${url}`);
    }
    throw new Error(`HTTP 요청에 실패했습니다: ${url}. ${error.message}`);
  }
};

const requestCsrfToken = async () => {
  const response = await fetchWithTimeout(`${baseUrl}/api/auth/csrf-token`);
  const setCookies =
    typeof response.headers.getSetCookie === 'function'
      ? response.headers.getSetCookie()
      : [response.headers.get('set-cookie') || ''];
  const tokenMatch = setCookies
    .map((cookie) => cookie.match(/(?:^|,\s*)XSRF-TOKEN=([^;]+)/))
    .find(Boolean);

  if (![200, 204].includes(response.status) || !tokenMatch) {
    throw new Error(`CSRF 토큰 발급에 실패했습니다. 상태 코드=${response.status}`);
  }

  return {
    headerValue: decodeURIComponent(tokenMatch[1]),
    cookieValue: tokenMatch[1],
  };
};

const csrfHeaders = (csrfToken) => ({
  [CSRF_HEADER_NAME]: csrfToken.headerValue,
  Cookie: `${CSRF_COOKIE_NAME}=${csrfToken.cookieValue}`,
});

const loginUser = async (email, password, csrfToken) => {
  const form = new URLSearchParams({username: email, password});
  const response = await fetchWithTimeout(`${baseUrl}/api/auth/sign-in`, {
    method: 'POST',
    headers: {
      ...csrfHeaders(csrfToken),
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: form,
  });
  const body = await response.json().catch(() => null);
  if (response.status !== 200 || !body?.accessToken) {
    throw new Error(`${email} 로그인 실패. 상태 코드=${response.status}`);
  }
  return body.accessToken;
};

const authHeadersWith = (accessToken, csrfToken) => ({
  ...csrfHeaders(csrfToken),
  Authorization: `Bearer ${accessToken}`,
  'Content-Type': 'application/json',
});

const fetchContentIds = async (accessToken, csrfToken, limit) => {
  const response = await fetchWithTimeout(
    `${baseUrl}/api/contents?sortBy=reviewCount&sortDirection=DESCENDING&limit=${limit}`,
    {headers: authHeadersWith(accessToken, csrfToken)},
  );
  const body = await response.json().catch(() => null);
  if (response.status !== 200 || !body?.data) {
    throw new Error(`콘텐츠 목록 조회 실패. 상태 코드=${response.status}`);
  }
  return body.data.map((c) => c.id);
};

const createPlaylist = async (accessToken, csrfToken, title, description) => {
  const response = await fetchWithTimeout(`${baseUrl}/api/playlists`, {
    method: 'POST',
    headers: authHeadersWith(accessToken, csrfToken),
    body: JSON.stringify({title, description}),
  });

  if (response.status === 201) {
    const body = await response.json();
    return {result: 'created', id: body.id};
  }

  return {result: 'failed'};
};

const addContentToPlaylist = async (accessToken, csrfToken, playlistId, contentId) => {
  const response = await fetchWithTimeout(
    `${baseUrl}/api/playlists/${playlistId}/contents/${contentId}`,
    {
      method: 'POST',
      headers: authHeadersWith(accessToken, csrfToken),
    },
  );

  return response.status === 204 ? 'added' : 'skipped';
};

const subscribePlaylist = async (accessToken, csrfToken, playlistId) => {
  const response = await fetchWithTimeout(
    `${baseUrl}/api/playlists/${playlistId}/subscription`,
    {
      method: 'POST',
      headers: authHeadersWith(accessToken, csrfToken),
    },
  );

  return response.status === 204 ? 'subscribed' : 'skipped';
};

const shuffleArray = (array) => {
  const shuffled = [...array];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled;
};

const randomItem = (array) => array[Math.floor(Math.random() * array.length)];

const mapWithConcurrency = async (items, workerCount, mapper) => {
  const results = new Array(items.length);
  let nextIndex = 0;
  const worker = async () => {
    while (nextIndex < items.length) {
      const currentIndex = nextIndex;
      nextIndex += 1;
      results[currentIndex] = await mapper(items[currentIndex], currentIndex);
    }
  };
  await Promise.all(Array.from({length: Math.min(workerCount, items.length)}, worker));
  return results;
};

const main = async () => {
  validateConfiguration();

  const usersFile = JSON.parse(await readFile(usersPath, 'utf8'));
  const users = usersFile.users;
  if (!users || users.length === 0) {
    throw new Error('users.json이 비어있습니다. 먼저 seed-users.mjs를 실행하세요.');
  }

  console.log(`${baseUrl} 대상 플레이리스트 시딩 시작`);
  console.log(`  유저 ${users.length}명, 유저당 플레이리스트 ${playlistsPerUser}개, 콘텐츠 ${contentsPerPlaylist}개, 구독 ${subscriptionsPerUser}개`);

  const csrfToken = await requestCsrfToken();

  // 콘텐츠 ID 풀 확보
  const firstToken = await loginUser(users[0].email, users[0].password, csrfToken);
  const allContentIds = await fetchContentIds(firstToken, csrfToken, 100);

  if (allContentIds.length === 0) {
    throw new Error('콘텐츠가 없습니다.');
  }

  console.log(`콘텐츠 ${allContentIds.length}개 확보 완료`);

  // Phase 1: 플레이리스트 생성 + 콘텐츠 추가
  const allPlaylistIds = []; // {playlistId, ownerIndex} 형태로 저장
  let totalPlaylists = 0;
  let totalContentsAdded = 0;
  let completed = 0;

  console.log('\n[Phase 1] 플레이리스트 생성 + 콘텐츠 추가');

  await mapWithConcurrency(users, concurrency, async (user, userIndex) => {
    const accessToken = await loginUser(user.email, user.password, csrfToken);

    for (let i = 0; i < playlistsPerUser; i++) {
      const title = `${randomItem(PLAYLIST_TITLES)} #${userIndex * playlistsPerUser + i + 1}`;
      const description = randomItem(PLAYLIST_DESCRIPTIONS);

      const playlist = await createPlaylist(accessToken, csrfToken, title, description);
      if (playlist.result !== 'created') {
        continue;
      }

      totalPlaylists++;
      allPlaylistIds.push({playlistId: playlist.id, ownerIndex: userIndex});

      // 콘텐츠 추가
      const contentIds = shuffleArray(allContentIds).slice(0, contentsPerPlaylist);
      for (const contentId of contentIds) {
        const result = await addContentToPlaylist(accessToken, csrfToken, playlist.id, contentId);
        if (result === 'added') {
          totalContentsAdded++;
        }
      }
    }

    completed++;
    if (completed % 50 === 0 || completed === users.length) {
      console.log(`  진행률: ${completed}/${users.length} (플레이리스트=${totalPlaylists}, 콘텐츠추가=${totalContentsAdded})`);
    }
  });

  console.log(`  완료: 플레이리스트 ${totalPlaylists}개 생성, 콘텐츠 ${totalContentsAdded}개 추가`);

  // Phase 2: 구독 (다른 유저의 플레이리스트만)
  let totalSubscriptions = 0;
  let totalSubSkipped = 0;
  completed = 0;

  console.log('\n[Phase 2] 플레이리스트 구독');

  await mapWithConcurrency(users, concurrency, async (user, userIndex) => {
    const accessToken = await loginUser(user.email, user.password, csrfToken);

    // 다른 유저의 플레이리스트만 필터
    const otherPlaylists = allPlaylistIds.filter((p) => p.ownerIndex !== userIndex);
    const targets = shuffleArray(otherPlaylists).slice(0, subscriptionsPerUser);

    for (const target of targets) {
      const result = await subscribePlaylist(accessToken, csrfToken, target.playlistId);
      if (result === 'subscribed') {
        totalSubscriptions++;
      } else {
        totalSubSkipped++;
      }
    }

    completed++;
    if (completed % 50 === 0 || completed === users.length) {
      console.log(`  진행률: ${completed}/${users.length} (구독=${totalSubscriptions}, 스킵=${totalSubSkipped})`);
    }
  });

  console.log(`  완료: 구독 ${totalSubscriptions}개 생성, 스킵 ${totalSubSkipped}개`);
  console.log(`\n플레이리스트 시딩 전체 완료!`);
};

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});

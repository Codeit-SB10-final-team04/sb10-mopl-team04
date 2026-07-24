import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

/**
 * users.json의 테스트 계정으로 로그인 후 리뷰를 대량 생성하는 시딩 스크립트
 *
 * 실행 예시:
 *   node k6/scripts/seed-reviews.mjs
 *
 * 환경 변수:
 *   BASE_URL             대상 서버 주소 (기본값: http://localhost:8080)
 *   REVIEWS_PER_USER     유저당 리뷰 수 (기본값: 20, 최대 50)
 *   SEED_CONCURRENCY     동시 처리 유저 수 (기본값: 5)
 *   ALLOW_REMOTE_SEED    원격 서버 시딩 허용 (기본값: false)
 */

const REQUEST_TIMEOUT_MS = 10_000;
const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
const MAX_REVIEWS_PER_USER = 50;

const baseUrl = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const reviewsPerUser = Math.min(
  Number.parseInt(process.env.REVIEWS_PER_USER || '20', 10),
  MAX_REVIEWS_PER_USER,
);
const concurrency = Number.parseInt(process.env.SEED_CONCURRENCY || '5', 10);
const allowRemoteSeed = (process.env.ALLOW_REMOTE_SEED || 'false').toLowerCase() === 'true';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const usersPath = path.resolve(scriptDirectory, '../data/users.json');

const REVIEW_TEXTS = [
  '정말 재밌었어요! 강추합니다.',
  '기대보다 별로였어요. 아쉽네요.',
  '배우들 연기가 좋았습니다. 몰입감 최고!',
  '스토리가 탄탄해서 몰입감 있었어요.',
  '시간 가는 줄 몰랐습니다. 다시 보고 싶어요.',
  '음악이 너무 좋았어요. OST 찾아봐야겠다.',
  '조금 지루한 부분도 있었지만 전반적으로 괜찮았어요.',
  '반전이 충격적이었어요. 소름 돋았다.',
  '가벼운 마음으로 보기 좋아요.',
  '연출이 독특하고 인상적이었습니다.',
  '기대 이상이었어요. 추천합니다!',
  '중반부터 살짝 늘어지긴 하지만 결말이 좋았어요.',
  '분위기가 너무 좋았어요. 영상미 최고.',
  '캐릭터가 매력적이라 감정이입이 잘 됐어요.',
  '이런 장르 좋아하시면 꼭 보세요.',
  '평점이 높길래 봤는데 기대만큼은 아니었어요.',
  '두 번째 보니까 더 재밌네요.',
  '가족이랑 같이 보기 좋은 작품이에요.',
  '결말이 좀 아쉬웠지만 과정은 좋았어요.',
  '올해 본 것 중에 최고였어요!',
];

const isLocalTarget = () => {
  const targetUrl = new URL(baseUrl);
  return new Set(['localhost', '127.0.0.1', '::1', '[::1]']).has(targetUrl.hostname);
};

const validateConfiguration = () => {
  if (!isLocalTarget() && !allowRemoteSeed) {
    throw new Error('원격 서버 시딩이 차단됐습니다. ALLOW_REMOTE_SEED=true를 명시해야 합니다.');
  }
  if (!Number.isInteger(reviewsPerUser) || reviewsPerUser < 1) {
    throw new Error('REVIEWS_PER_USER는 1 이상이어야 합니다.');
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

const fetchContentIds = async (accessToken, csrfToken, limit) => {
  const response = await fetchWithTimeout(
    `${baseUrl}/api/contents?sortBy=reviewCount&sortDirection=ASCENDING&limit=${limit}`,
    {
      headers: {
        ...csrfHeaders(csrfToken),
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
    },
  );
  const body = await response.json().catch(() => null);
  if (response.status !== 200 || !body?.data) {
    throw new Error(`콘텐츠 목록 조회 실패. 상태 코드=${response.status}`);
  }
  return body.data.map((c) => c.id);
};

const createReview = async (accessToken, csrfToken, contentId, text, rating) => {
  const response = await fetchWithTimeout(`${baseUrl}/api/reviews`, {
    method: 'POST',
    headers: {
      ...csrfHeaders(csrfToken),
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({contentId, text, rating}),
  });

  if (response.status === 200) {
    return 'created';
  }

  const body = await response.json().catch(() => null);
  if (response.status === 400 && body?.exceptionName === 'ReviewException') {
    return 'skipped';
  }

  throw new Error(`리뷰 작성 실패. 상태 코드=${response.status} 응답=${JSON.stringify(body)}`);
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
const randomRating = () => Math.floor(Math.random() * 5) + 1;

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

  console.log(`${baseUrl} 대상 리뷰 시딩 시작 (유저 ${users.length}명, 유저당 ${reviewsPerUser}개)`);

  const csrfToken = await requestCsrfToken();

  // 첫 유저로 로그인해서 콘텐츠 ID 풀 확보
  const firstToken = await loginUser(users[0].email, users[0].password, csrfToken);
  const allContentIds = await fetchContentIds(firstToken, csrfToken, 100);

  if (allContentIds.length === 0) {
    throw new Error('콘텐츠가 없습니다. 리뷰를 작성할 수 없습니다.');
  }

  console.log(`콘텐츠 ${allContentIds.length}개 확보 완료`);

  let totalCreated = 0;
  let totalSkipped = 0;
  let completed = 0;

  await mapWithConcurrency(users, concurrency, async (user) => {
    const accessToken = await loginUser(user.email, user.password, csrfToken);
    const targetContentIds = shuffleArray(allContentIds).slice(0, reviewsPerUser);

    let userCreated = 0;
    let userSkipped = 0;

    for (const contentId of targetContentIds) {
      const result = await createReview(
        accessToken,
        csrfToken,
        contentId,
        randomItem(REVIEW_TEXTS),
        randomRating(),
      );
      if (result === 'created') {
        userCreated++;
      } else {
        userSkipped++;
      }
    }

    totalCreated += userCreated;
    totalSkipped += userSkipped;
    completed++;

    if (completed % 50 === 0 || completed === users.length) {
      console.log(`리뷰 시딩 진행률: ${completed}/${users.length} (생성=${totalCreated}, 스킵=${totalSkipped})`);
    }
  });

  console.log(`리뷰 시딩 완료: 생성=${totalCreated}, 스킵(이미 존재)=${totalSkipped}`);
};

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});

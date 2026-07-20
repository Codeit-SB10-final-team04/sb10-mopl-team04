import {mkdir, rename, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

/**
 * 실제 회원가입/로그인 API를 사용해 테스트 계정과 유효한 JWT 준비
 *
 * PowerShell 실행 예시:
 *   $env:USER_COUNT = "100"
 *   $env:LOAD_TEST_PASSWORD = "기본값을 변경할 경우에만 입력"
 *   node .\k6\scripts\seed-users.mjs
 *
 * 환경 변수:
 *   BASE_URL           대상 서버 주소 (기본값: http://localhost:8080)
 *   USER_COUNT         생성할 사용자 수, 100~200 (기본값: 100)
 *   USER_PREFIX        이메일 접두사 (기본값: loadtest)
 *   LOAD_TEST_PASSWORD 테스트 계정 비밀번호 (기본값: Mopl-load-2026!)
 *   SEED_CONCURRENCY   동시에 처리할 사용자 수 (기본값: 5)
 *   ALLOW_REMOTE_SEED  원격 서버 시딩 허용 여부 (기본값: false)
 */

const MIN_USER_COUNT = 100;
const MAX_USER_COUNT = 200;
const DEFAULT_PASSWORD = 'Mopl-load-2026!';
const REQUEST_TIMEOUT_MS = 10_000;
const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';

const baseUrl = (process.env.BASE_URL || 'http://localhost:8080').replace(
  /\/+$/,
  '',
);
const userCount = Number.parseInt(process.env.USER_COUNT || '100', 10);
const concurrency = Number.parseInt(
  process.env.SEED_CONCURRENCY || '5',
  10,
);
const password = process.env.LOAD_TEST_PASSWORD || DEFAULT_PASSWORD;
const allowRemoteSeed =
  (process.env.ALLOW_REMOTE_SEED || 'false').toLowerCase() === 'true';
const userPrefix = (process.env.USER_PREFIX || 'loadtest')
  .toLowerCase()
  .replace(/[^a-z0-9-]/g, '');

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const dataDirectory = path.resolve(scriptDirectory, '../data');
const outputPath = path.join(dataDirectory, 'users.json');
const temporaryOutputPath = `${outputPath}.tmp`;

// 원격 서버 오시딩 방지를 위한 대상 주소 판별
const isLocalTarget = () => {
  let targetUrl;

  try {
    targetUrl = new URL(baseUrl);
  } catch {
    throw new Error(`BASE_URL 형식이 올바르지 않습니다: ${baseUrl}`);
  }

  if (!['http:', 'https:'].includes(targetUrl.protocol)) {
    throw new Error('BASE_URL 프로토콜은 HTTP 또는 HTTPS여야 합니다.');
  }

  return new Set(['localhost', '127.0.0.1', '::1', '[::1]']).has(
    targetUrl.hostname,
  );
};

// 네트워크 요청 전 입력값과 원격 시딩 안전 조건 검증
const validateConfiguration = () => {
  const localTarget = isLocalTarget();

  if (
    !Number.isInteger(userCount) ||
    userCount < MIN_USER_COUNT ||
    userCount > MAX_USER_COUNT
  ) {
    throw new Error(
      `USER_COUNT는 ${MIN_USER_COUNT} 이상 ${MAX_USER_COUNT} 이하여야 합니다.`,
    );
  }

  if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 20) {
    throw new Error('SEED_CONCURRENCY는 1 이상 20 이하여야 합니다.');
  }

  if (!userPrefix) {
    throw new Error('USER_PREFIX에는 영문자 또는 숫자가 하나 이상 필요합니다.');
  }

  if (password.length < 8) {
    throw new Error('LOAD_TEST_PASSWORD는 8자 이상이어야 합니다.');
  }

  if (!localTarget && !allowRemoteSeed) {
    throw new Error(
      '원격 서버 시딩이 차단됐습니다. 실행하려면 ALLOW_REMOTE_SEED=true를 명시해야 합니다.',
    );
  }

  if (!localTarget && password === DEFAULT_PASSWORD) {
    throw new Error(
      '원격 서버에는 기본 비밀번호를 사용할 수 없습니다. LOAD_TEST_PASSWORD를 직접 지정해야 합니다.',
    );
  }
};

// 오류 응답도 진단할 수 있도록 JSON과 일반 문자열 처리
const readResponseBody = async (response) => {
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
};

const bodyForError = (body) =>
  typeof body === 'string' ? body : JSON.stringify(body);

// 응답 없는 서버에서 스크립트가 장시간 대기하지 않도록 요청 시간 제한
const fetchWithTimeout = async (url, options = {}) => {
  try {
    return await fetch(url, {
      ...options,
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (error) {
    if (error?.name === 'TimeoutError' || error?.name === 'AbortError') {
      throw new Error(
        `HTTP 요청 시간이 ${REQUEST_TIMEOUT_MS / 1000}초를 초과했습니다: ${url}`,
      );
    }

    throw new Error(`HTTP 요청에 실패했습니다: ${url}. ${error.message}`);
  }
};

// CookieCsrfTokenRepository가 Set-Cookie로 발급한 토큰을 헤더와 쿠키에 재사용
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
    throw new Error(
      `CSRF 토큰 발급에 실패했습니다. 상태 코드=${response.status}`,
    );
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

const login = async (email, csrfToken) => {
  const form = new URLSearchParams({
    username: email,
    password,
  });
  const response = await fetchWithTimeout(`${baseUrl}/api/auth/sign-in`, {
    method: 'POST',
    headers: {
      ...csrfHeaders(csrfToken),
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: form,
  });
  const body = await readResponseBody(response);

  if (response.status !== 200 || !body?.userDto || !body?.accessToken) {
    throw new Error(
      `${email} 로그인에 실패했습니다. ` +
        `상태 코드=${response.status} 응답=${bodyForError(body)}`,
    );
  }

  return body;
};

// 다른 400 응답과 구분하기 위한 중복 이메일 응답 식별
const isDuplicateEmailResponse = (response, body, email) =>
  response.status === 400 &&
  body?.exceptionName === 'UserException' &&
  body?.message === '이미 사용 중인 이메일입니다.' &&
  body?.details?.email === email;

// 회원가입 성공 시 신규 사용자, 중복 이메일이면 기존 테스트 사용자를 재사용
const createOrReuseUser = async (definition, csrfToken) => {
  const response = await fetchWithTimeout(`${baseUrl}/api/users`, {
    method: 'POST',
    headers: {
      ...csrfHeaders(csrfToken),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      name: definition.name,
      email: definition.email,
      password,
    }),
  });
  const body = await readResponseBody(response);
  const duplicateEmail = isDuplicateEmailResponse(
    response,
    body,
    definition.email,
  );

  if (response.status !== 201 && !duplicateEmail) {
    throw new Error(
      `${definition.email} 사용자 생성에 실패했습니다. ` +
        `상태 코드=${response.status} 응답=${bodyForError(body)}`,
    );
  }

  try {
    const jwt = await login(definition.email, csrfToken);
    return {
      created: response.status === 201,
      jwt,
    };
  } catch (error) {
    if (duplicateEmail) {
      throw new Error(
        `${definition.email} 계정이 이미 존재하지만 재사용할 수 없습니다. ` +
          `다른 USER_PREFIX 또는 기존 비밀번호가 필요합니다. ${error.message}`,
      );
    }

    throw error;
  }
};

// JWT 서명을 검증하지 않고 exp 값만 읽어 fixture의 만료 시각 표시
const accessTokenExpiresAt = (accessToken) => {
  try {
    const payload = JSON.parse(
      Buffer.from(accessToken.split('.')[1], 'base64url').toString('utf8'),
    );
    return Number.isFinite(payload.exp)
      ? new Date(payload.exp * 1000).toISOString()
      : null;
  } catch {
    return null;
  }
};

// 입력 순서를 유지하면서 서버 요청 수를 제한하는 병렬 처리
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

  await Promise.all(
    Array.from({length: Math.min(workerCount, items.length)}, worker),
  );
  return results;
};

const main = async () => {
  validateConfiguration();

  if (!isLocalTarget()) {
    console.warn(`원격 서버 시딩 명시적 허용: ${baseUrl}`);
  }

  console.log(`${baseUrl} 대상 테스트 사용자 ${userCount}명 시딩 시작`);
  const csrfToken = await requestCsrfToken();

  // 사용자 풀 구성
  const definitions = Array.from({length: userCount}, (_, index) => {
    const sequence = String(index + 1).padStart(3, '0');

    return {
      index,
      name: `Load Test User ${sequence}`,
      email: `${userPrefix}${sequence}@mopl.test`,
      pool: index < Math.ceil(userCount / 2) ? 'A' : 'B',
    };
  });

  let completed = 0;
  let createdCount = 0;
  const users = await mapWithConcurrency(
    definitions,
    concurrency,
    async (definition) => {
      const result = await createOrReuseUser(definition, csrfToken);
      completed += 1;
      createdCount += result.created ? 1 : 0;

      if (completed % 10 === 0 || completed === userCount) {
        console.log(`테스트 사용자 준비 진행률: ${completed}/${userCount}`);
      }

      return {
        index: definition.index,
        id: result.jwt.userDto.id,
        name: result.jwt.userDto.name,
        email: result.jwt.userDto.email,
        password,
        pool: definition.pool,
        accessToken: result.jwt.accessToken,
        accessTokenExpiresAt: accessTokenExpiresAt(result.jwt.accessToken),
      };
    },
  );

  // k6 시나리오에서 즉시 사용할 계정 정보와 JWT fixture 구성
  const fixture = {
    version: 1,
    generatedAt: new Date().toISOString(),
    baseUrl,
    count: users.length,
    createdCount,
    reusedCount: users.length - createdCount,
    users,
  };

  // 중간 실패 시 기존 fixture를 망가뜨리지 않도록 임시 파일 작성 후 교체
  await mkdir(dataDirectory, {recursive: true});
  await writeFile(
    temporaryOutputPath,
    `${JSON.stringify(fixture, null, 2)}\n`,
    'utf8',
  );
  await rename(temporaryOutputPath, outputPath);

  console.log(
    `사용자 fixture 저장 완료: ${outputPath} ` +
      `(신규=${createdCount}, 재사용=${users.length - createdCount})`,
  );
  console.log('비밀번호와 JWT가 포함된 users.json의 Git 커밋 금지');
};

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});

import {check, fail} from 'k6';
import http from 'k6/http';

import {BASE_URL} from './config.js';

const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';

// k6의 각 VU는 모듈 상태를 독립적으로 가지므로 로그인한 VU의 토큰만 보관
let activeCsrfToken = null;

// Spring Security가 응답 쿠키로 발급한 CSRF 토큰 값 추출
const csrfCookieFrom = (response) => {
  const cookies = response.cookies[CSRF_COOKIE_NAME];
  return cookies && cookies.length > 0 ? cookies[0].value : null;
};

// 로그인 전 토큰 발급
const getCsrfToken = () => {
  const response = http.get(`${BASE_URL}/api/auth/csrf-token`, {
    tags: {endpoint: 'auth_csrf'},
  });
  const token = csrfCookieFrom(response);
  const succeeded = check(response, {
    'CSRF 토큰 발급 성공': (res) =>
      (res.status === 200 || res.status === 204) && token !== null,
  });

  if (!succeeded) {
    fail(`CSRF 토큰 발급 실패. 상태 코드=${response.status}`);
  }

  activeCsrfToken = token;
  return token;
};

// 로그인 후 API 요청에 사용할 access token 반환
export const login = (email, password) => {
  const csrfToken = getCsrfToken();
  const body =
    `username=${encodeURIComponent(email)}` +
    `&password=${encodeURIComponent(password)}`;

  const response = http.post(`${BASE_URL}/api/auth/sign-in`, body, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      [CSRF_HEADER_NAME]: csrfToken,
      Cookie: `${CSRF_COOKIE_NAME}=${csrfToken}`,
    },
    tags: {endpoint: 'auth_sign_in'},
  });
  const accessToken =
    response.status === 200 ? response.json('accessToken') : null;
  const succeeded = check(response, {
    '로그인 성공': (res) => res.status === 200,
    '액세스 토큰 발급 성공': () => Boolean(accessToken),
  });

  if (!succeeded) {
    fail(`${email} 로그인 실패. 상태 코드=${response.status}`);
  }

  return accessToken;
};

// 쓰기 API도 바로 호출할 수 있도록 Bearer 인증과 CSRF 정보를 함께 반환
export const authHeaders = (accessToken) => {
  if (!activeCsrfToken) {
    fail('authHeaders 호출 전에 login() 실행이 필요합니다.');
  }

  return {
    Authorization: `Bearer ${accessToken}`,
    [CSRF_HEADER_NAME]: activeCsrfToken,
    Cookie: `${CSRF_COOKIE_NAME}=${activeCsrfToken}`,
  };
};

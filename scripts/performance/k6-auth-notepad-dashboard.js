import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const notepadDuration = new Trend('notepad_list_duration', true);
const dashboardDuration = new Trend('dashboard_stats_duration', true);
const notepadErrors = new Rate('notepad_list_errors');
const dashboardErrors = new Rate('dashboard_stats_errors');

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    notepad_list: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      exec: 'notepadListScenario',
    },
    dashboard_stats: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
      exec: 'dashboardScenario',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PARENT_EMAIL = __ENV.PARENT_EMAIL || 'parent1@test.com';
const PARENT_PASSWORD = __ENV.PARENT_PASSWORD || 'test1234!';
const PRINCIPAL_EMAIL = __ENV.PRINCIPAL_EMAIL || 'principal@test.com';
const PRINCIPAL_PASSWORD = __ENV.PRINCIPAL_PASSWORD || 'test1234!';
const CLASSROOM_ID = __ENV.CLASSROOM_ID || '1';

function loginAndGetCookies(email, password) {
  const csrfResponse = http.get(`${BASE_URL}/login`);
  const csrfCookie = csrfResponse.cookies['XSRF-TOKEN']?.[0]?.value;

  check(csrfResponse, {
    'login page status is 200': (r) => r.status === 200,
    'csrf cookie is present': () => Boolean(csrfCookie),
  });

  const loginBody = JSON.stringify({
    email,
    password,
  });

  const res = http.post(`${BASE_URL}/api/v1/auth/login`, loginBody, {
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfCookie,
    },
    cookies: { 'XSRF-TOKEN': csrfCookie },
  });

  check(res, {
    'login status is 200': (r) => r.status === 200,
  });

  return res.cookies;
}

export function notepadListScenario() {
  const cookies = loginAndGetCookies(PARENT_EMAIL, PARENT_PASSWORD);

  const res = http.get(
    `${BASE_URL}/api/v1/notepads/classroom/${CLASSROOM_ID}?page=0&size=20`,
    { cookies }
  );

  check(res, {
    'notepad list status is 200': (r) => r.status === 200,
    'notepad list success true': (r) => r.body.includes('"success":true'),
  });

  notepadDuration.add(res.timings.duration);
  notepadErrors.add(res.status !== 200);

  sleep(1);
}

export function dashboardScenario() {
  const cookies = loginAndGetCookies(PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);

  const res = http.get(`${BASE_URL}/api/v1/dashboard/statistics`, {
    cookies,
  });

  check(res, {
    'dashboard status is 200': (r) => r.status === 200,
  });

  dashboardDuration.add(res.timings.duration);
  dashboardErrors.add(res.status !== 200);

  sleep(1);
}

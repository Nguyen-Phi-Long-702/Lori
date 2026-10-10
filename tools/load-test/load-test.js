// load-test.js: load test co ban cho cac API CRUD cua Lori Backend (ke hoach Tuan 5, Ngay 7).
// Cach chay: xem INFRA.md muc 13. File KHONG chua bi mat: moi gia tri nhay cam lay tu bien moi truong
// BASE_URL, API_KEY, APP_SIGNATURE.
//
// Backend gioi han 60 request/phut/IP (RateLimitFilter) nen script chi gui khoang 50 request/phut:
// 10 vong/phut, moi vong 5 request, trong 3 phut. Day KHONG phai test nhieu nguoi dung dong thoi.
// Chuan hieu nang CRUD (ke hoach muc 20): avg < 300ms, p95 < 500ms cho tung API.

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL;
const API_KEY = __ENV.API_KEY;
const APP_SIGNATURE = __ENV.APP_SIGNATURE;

// Moi API gan 1 tag "endpoint" rieng de bao cao avg/p95 tung API
const ENDPOINTS = ['content_version', 'users_me', 'progress_pull', 'progress_sync', 'exams_list'];

const thresholds = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
};
for (const endpoint of ENDPOINTS) {
  thresholds[`http_req_duration{endpoint:${endpoint}}`] = ['avg<300', 'p(95)<500'];
}

export const options = {
  scenarios: {
    crud: {
      executor: 'constant-arrival-rate',
      rate: 10,
      timeUnit: '1m',
      duration: '3m',
      preAllocatedVUs: 2,
      maxVUs: 5,
    },
  },
  thresholds,
};

function headers(token) {
  const result = {
    'Content-Type': 'application/json',
    'X-API-Key': API_KEY,
    'X-App-Signature': APP_SIGNATURE,
  };
  if (token) {
    result.Authorization = `Bearer ${token}`;
  }
  return result;
}

// Chay 1 lan truoc khi do: tao 1 user moi va cho len Premium (mock) de goi duoc /api/exams.
// Cac request nay gan tag endpoint=setup nen khong tinh vao nguong cua tung API.
export function setup() {
  if (!BASE_URL || !API_KEY || !APP_SIGNATURE) {
    throw new Error('Thieu bien moi truong BASE_URL, API_KEY hoac APP_SIGNATURE');
  }

  const register = http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify({
      displayName: 'k6 Load Test',
      email: `k6.${Date.now()}@example.com`,
      password: `K6-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    }),
    { headers: headers(), tags: { endpoint: 'setup' } });
  if (register.status !== 201) {
    throw new Error(`Register that bai: HTTP ${register.status} ${register.body}`);
  }
  const token = register.json('accessToken');

  const purchase = http.post(
    `${BASE_URL}/api/payment/mock-purchase`,
    JSON.stringify({ planType: 'MONTHLY' }),
    { headers: headers(token), tags: { endpoint: 'setup' } });
  if (purchase.status !== 201) {
    throw new Error(`Mock purchase that bai: HTTP ${purchase.status} ${purchase.body}`);
  }

  return { token };
}

// Moi vong: 5 request (4 GET + 1 POST) vao 5 API CRUD
export default function (data) {
  const params = (endpoint) => ({ headers: headers(data.token), tags: { endpoint } });

  const version = http.get(`${BASE_URL}/api/content/version`, params('content_version'));
  check(version, {
    'content/version tra 200': (r) => r.status === 200,
  });

  const me = http.get(`${BASE_URL}/api/users/me`, params('users_me'));
  check(me, {
    'users/me tra 200': (r) => r.status === 200,
    'users/me la Premium': (r) => r.json('premium') === true,
  });

  const pull = http.get(`${BASE_URL}/api/progress/pull`, params('progress_pull'));
  check(pull, {
    'progress/pull tra 200': (r) => r.status === 200,
    'progress/pull tra mang': (r) => Array.isArray(r.json()),
  });

  const sync = http.post(
    `${BASE_URL}/api/progress/sync`,
    JSON.stringify({
      items: [{
        itemType: 'grammar_lesson',
        itemId: 1,
        status: 'completed',
        correctCount: 1,
        incorrectCount: 0,
        lastStudiedAt: new Date().toISOString(),
      }],
    }),
    params('progress_sync'));
  check(sync, {
    'progress/sync tra 200': (r) => r.status === 200,
    'progress/sync nhan 1 muc': (r) => r.json('received') === 1,
  });

  const exams = http.get(`${BASE_URL}/api/exams`, params('exams_list'));
  check(exams, {
    'exams tra 200': (r) => r.status === 200,
    'exams co it nhat 1 de': (r) => Array.isArray(r.json()) && r.json().length > 0,
  });
}
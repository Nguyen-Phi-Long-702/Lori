// load-test-concurrent.js: load test 100 nguoi dung ao dong thoi cho cac API CRUD cua Lori Backend tren URL Render that
// (ke hoach Tuan 17, Ngay 4-5). Cach chay: xem INFRA.md muc 40. File KHONG chua bi mat: moi gia tri nhay cam lay tu
// bien moi truong BASE_URL, API_KEY, APP_SIGNATURE.
//
// RateLimitFilter gioi han 60 request/phut/IP va lay IP tu gia tri dau tien cua X-Forwarded-For. Moi nguoi dung ao gui
// mot X-Forwarded-For rieng de mo phong 100 nguoi o 100 dia chi IP khac nhau, moi nguoi nghi 1 giay giua 2 request
// (duoi 1 request/giay) nen khong vuot gioi han cua rieng minh.
// Chuan hieu nang CRUD (ke hoach muc 20): avg < 300ms, p95 < 500ms cho tung API.

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL;
const API_KEY = __ENV.API_KEY;
const APP_SIGNATURE = __ENV.APP_SIGNATURE;

const VIRTUAL_USERS = 100;
// So tai khoan that duoc tao; moi tai khoan dung chung cho VIRTUAL_USERS / ACCOUNTS nguoi dung ao
const ACCOUNTS = 10;

// Moi API gan 1 tag "endpoint" rieng de bao cao avg/p95 tung API
const ENDPOINTS = ['content_version', 'users_me', 'progress_pull', 'progress_sync', 'exams_list'];

const thresholds = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
};
for (const endpoint of ENDPOINTS) {
  thresholds[`http_req_duration{endpoint:${endpoint}}`] = ['avg<300', 'p(95)<500'];
}
// Co 429 nghia la X-Forwarded-For rieng cua tung nguoi dung ao khong con tac dung (Render ghi de hoac Backend doi cach lay IP)
thresholds['http_reqs{status:429}'] = ['count==0'];

export const options = {
  setupTimeout: '5m',
  scenarios: {
    concurrent: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: VIRTUAL_USERS },
        { duration: '2m', target: VIRTUAL_USERS },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds,
};

function headers(token, clientIp) {
  const result = {
    'Content-Type': 'application/json',
    'X-API-Key': API_KEY,
    'X-App-Signature': APP_SIGNATURE,
    'X-Forwarded-For': clientIp,
  };
  if (token) {
    result.Authorization = `Bearer ${token}`;
  }
  return result;
}

// runId khac nhau moi lan chay de lan chay sau khong dung lai bucket rate limit cua lan truoc (con song toi 1 phut)
function clientIp(runId, vu) {
  return `10.${runId}.0.${vu}`;
}

// Chay 1 lan truoc khi do: tao ACCOUNTS tai khoan moi, nang len Premium (mock) de goi duoc /api/exams.
// Cac request nay gan tag endpoint=setup nen khong tinh vao nguong cua tung API.
export function setup() {
  if (!BASE_URL || !API_KEY || !APP_SIGNATURE) {
    throw new Error('Thieu bien moi truong BASE_URL, API_KEY hoac APP_SIGNATURE');
  }

  const runId = 1 + Math.floor(Math.random() * 250);
  const setupIp = `10.${runId}.1.1`;
  const tokens = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const register = http.post(
      `${BASE_URL}/api/auth/register`,
      JSON.stringify({
        displayName: 'k6 Load Test',
        email: `k6c.${Date.now()}.${i}@example.com`,
        password: `K6-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      }),
      { headers: headers(null, setupIp), tags: { endpoint: 'setup' } });
    if (register.status !== 201) {
      throw new Error(`Register that bai: HTTP ${register.status} ${register.body}`);
    }
    const token = register.json('accessToken');

    const purchase = http.post(
      `${BASE_URL}/api/payment/mock-purchase`,
      JSON.stringify({ planType: 'MONTHLY' }),
      { headers: headers(token, setupIp), tags: { endpoint: 'setup' } });
    if (purchase.status !== 201) {
      throw new Error(`Mock purchase that bai: HTTP ${purchase.status} ${purchase.body}`);
    }
    tokens.push(token);
  }
  return { tokens, runId };
}

// Moi vong: 5 request vao 5 API CRUD (4 GET + 1 POST), nghi 1 giay sau moi request
export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const ip = clientIp(data.runId, __VU);
  const params = (endpoint) => ({ headers: headers(token, ip), tags: { endpoint } });

  const version = http.get(`${BASE_URL}/api/content/version`, params('content_version'));
  check(version, {
    'content/version tra 200': (r) => r.status === 200,
  });
  sleep(1);

  const me = http.get(`${BASE_URL}/api/users/me`, params('users_me'));
  check(me, {
    'users/me tra 200': (r) => r.status === 200,
    'users/me la Premium': (r) => r.json('premium') === true,
  });
  sleep(1);

  const pull = http.get(`${BASE_URL}/api/progress/pull`, params('progress_pull'));
  check(pull, {
    'progress/pull tra 200': (r) => r.status === 200,
    'progress/pull tra mang': (r) => Array.isArray(r.json()),
  });
  sleep(1);

  // itemId = so thu tu nguoi dung ao: moi nguoi ghi dong rieng, khong tranh chap khoa dong giua cac nguoi dung ao
  const sync = http.post(
    `${BASE_URL}/api/progress/sync`,
    JSON.stringify({
      items: [{
        itemType: 'grammar_lesson',
        itemId: __VU,
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
  sleep(1);

  const exams = http.get(`${BASE_URL}/api/exams`, params('exams_list'));
  check(exams, {
    'exams tra 200': (r) => r.status === 200,
    'exams co it nhat 1 de': (r) => Array.isArray(r.json()) && r.json().length > 0,
  });
  sleep(1);
}

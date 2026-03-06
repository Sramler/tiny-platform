import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const FLOW = (__ENV.FLOW || 'async').toLowerCase();
const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:9000').replace(/\/+$/, '');
const EXPORT_PREFIX = __ENV.EXPORT_PREFIX || '/export';
const AUTH_MODE = (__ENV.AUTH_MODE || (__ENV.ACCESS_TOKEN ? 'bearer' : 'session')).toLowerCase();
const USER_AGENT = __ENV.USER_AGENT || 'TinyPerfK6/1.0';

const VUS = Number(__ENV.VUS || 2);
const DURATION = __ENV.DURATION || '1m';
const GRACEFUL_STOP = __ENV.GRACEFUL_STOP || '30s';
const MAX_POLL_SECONDS = Number(__ENV.MAX_POLL_SECONDS || 300);
const POLL_INTERVAL_SECONDS = Number(__ENV.POLL_INTERVAL_SECONDS || 2);
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || 0.5);

const DOWNLOAD_ON_SUCCESS = String(__ENV.DOWNLOAD_ON_SUCCESS || 'true').toLowerCase() === 'true';
const DISCARD_BODIES_REQUESTED = String(__ENV.DISCARD_RESPONSE_BODIES || 'false').toLowerCase() === 'true';
const DISCARD_BODIES = DISCARD_BODIES_REQUESTED && AUTH_MODE === 'bearer' && FLOW === 'sync';
const SUBMIT_ALLOWED_REJECT_STATUSES = parseStatusSet(__ENV.SUBMIT_ALLOWED_REJECT_STATUSES || '');
const EXPECTED_SUBMIT_OK_RATE_MIN = Number(__ENV.EXPECTED_SUBMIT_OK_RATE_MIN || (SUBMIT_ALLOWED_REJECT_STATUSES.size > 0 ? 0 : 0.98));
const EXPECTED_SUBMIT_REJECT_RATE_MIN = Number(__ENV.EXPECTED_SUBMIT_REJECT_RATE_MIN || 0);
const EXPECTED_SUBMIT_REJECT_RATE_MAX = Number(__ENV.EXPECTED_SUBMIT_REJECT_RATE_MAX || (SUBMIT_ALLOWED_REJECT_STATUSES.size > 0 ? 1 : 0));

const submitMs = new Trend('export_submit_ms');
const syncMs = new Trend('export_sync_ms');
const endToEndMs = new Trend('export_end_to_end_ms');
const pollRoundtripMs = new Trend('export_poll_roundtrip_ms');
const downloadMs = new Trend('export_download_ms');

const submitOkRate = new Rate('export_submit_ok_rate');
const submitRejectedRate = new Rate('export_submit_rejected_rate');
const taskSuccessRate = new Rate('export_task_success_rate');
const downloadOkRate = new Rate('export_download_ok_rate');

const taskFailedCount = new Counter('export_task_failed_count');
const taskTimeoutCount = new Counter('export_task_timeout_count');
const submitRejectedCount = new Counter('export_submit_rejected_count');

const defaultThresholds = buildDefaultThresholds();

const syncThresholds = {
  ...defaultThresholds,
  export_sync_ms: ['p(95)<180000'],
};

const asyncThresholds = {
  ...defaultThresholds,
  export_end_to_end_ms: ['p(95)<300000'],
  export_download_ok_rate: ['rate>0.95'],
};

export const options = {
  discardResponseBodies: DISCARD_BODIES,
  scenarios: {
    export_flow: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      gracefulStop: GRACEFUL_STOP,
    },
  },
  thresholds: FLOW === 'sync' ? syncThresholds : asyncThresholds,
};

let sessionReady = false;
let bearerToken = __ENV.ACCESS_TOKEN || '';

function parseStatusSet(raw) {
  return new Set(
    String(raw || '')
      .split(',')
      .map((part) => Number(String(part).trim()))
      .filter((status) => Number.isInteger(status) && status > 0),
  );
}

function buildRateThresholds(metric, min, max) {
  const thresholds = [];
  if (Number.isFinite(min) && min > 0) {
    thresholds.push(`rate>=${min}`);
  }
  if (Number.isFinite(max) && max < 1) {
    thresholds.push(`rate<=${max}`);
  }
  return thresholds.length > 0 ? { [metric]: thresholds } : {};
}

function buildDefaultThresholds() {
  return {
    http_req_failed: ['rate<0.02'],
    ...buildRateThresholds('export_submit_ok_rate', EXPECTED_SUBMIT_OK_RATE_MIN, 1),
    ...buildRateThresholds('export_submit_rejected_rate', EXPECTED_SUBMIT_REJECT_RATE_MIN, EXPECTED_SUBMIT_REJECT_RATE_MAX),
    export_task_success_rate: ['rate>0.95'],
    export_submit_ms: ['p(95)<2000'],
  };
}

function parseJsonResponse(res) {
  if (!res || !res.body) {
    return null;
  }
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function exportRequestPayload() {
  const tenantId = Number(__ENV.TENANT_ID || 1);
  const pageSize = Number(__ENV.PAGE_SIZE || 5000);
  const fileNamePrefix = __ENV.FILE_NAME_PREFIX || 'k6-export';
  const exportType = __ENV.EXPORT_TYPE || 'demo_export_usage';

  return {
    fileName: `${fileNamePrefix}-${Date.now()}-vu${__VU}-it${__ITER}`,
    async: FLOW !== 'sync',
    pageSize,
    sheets: [
      {
        sheetName: __ENV.SHEET_NAME || 'demo_export_usage',
        exportType,
        filters: {
          tenantId,
        },
        columns: [
          { title: 'ID', field: 'id', children: [] },
          { title: 'TenantID', field: 'tenantId', children: [] },
          { title: 'UsageDate', field: 'usageDate', children: [] },
          { title: 'ProductCode', field: 'productCode', children: [] },
          { title: 'ProductName', field: 'productName', children: [] },
          { title: 'UsageQty', field: 'usageQty', children: [] },
          { title: 'Amount', field: 'amount', children: [] },
          { title: 'Currency', field: 'currency', children: [] },
          { title: 'Status', field: 'status', children: [] },
          { title: 'CreatedAt', field: 'createdAt', children: [] },
        ],
      },
    ],
  };
}

function bearerHeaders(extraHeaders = {}) {
  if (!bearerToken) {
    fail('AUTH_MODE=bearer but ACCESS_TOKEN is empty');
  }
  return {
    'User-Agent': USER_AGENT,
    Authorization: `Bearer ${bearerToken}`,
    ...extraHeaders,
  };
}

function ensureSessionLogin() {
  if (sessionReady) {
    return;
  }

  const username = __ENV.LOGIN_USERNAME;
  const password = __ENV.LOGIN_PASSWORD;
  const tenantId = __ENV.LOGIN_TENANT_ID || '1';

  if (!username || !password) {
    fail('AUTH_MODE=session requires LOGIN_USERNAME and LOGIN_PASSWORD');
  }

  const csrfRes = http.get(`${BASE_URL}/csrf`, {
    headers: { 'User-Agent': USER_AGENT },
    tags: { flow: FLOW, step: 'csrf' },
  });
  check(csrfRes, {
    'csrf status is 200': (r) => r.status === 200,
  });
  const csrf = parseJsonResponse(csrfRes);
  if (!csrf || !csrf.token) {
    fail('failed to fetch csrf token');
  }

  const loginBody = {
    username,
    password,
    tenantId,
    authenticationProvider: __ENV.LOGIN_PROVIDER || 'LOCAL',
    authenticationType: __ENV.LOGIN_TYPE || 'PASSWORD',
    [csrf.parameterName || '_csrf']: csrf.token,
  };

  const loginRes = http.post(`${BASE_URL}/login`, loginBody, {
    redirects: 0,
    headers: {
      'User-Agent': USER_AGENT,
      'Content-Type': 'application/x-www-form-urlencoded',
      [csrf.headerName || 'X-XSRF-TOKEN']: csrf.token,
    },
    tags: { flow: FLOW, step: 'login' },
  });

  const loginOk = check(loginRes, {
    'login status is 200/302': (r) => r.status === 200 || r.status === 302,
  });
  if (!loginOk) {
    fail(`login failed, status=${loginRes.status}, body=${loginRes.body}`);
  }
  sessionReady = true;
}

function authHeaders(extraHeaders = {}) {
  if (AUTH_MODE === 'bearer') {
    return bearerHeaders(extraHeaders);
  }
  if (AUTH_MODE === 'session') {
    ensureSessionLogin();
    return {
      'User-Agent': USER_AGENT,
      ...extraHeaders,
    };
  }
  fail(`unsupported AUTH_MODE=${AUTH_MODE}, expected bearer or session`);
  return extraHeaders;
}

function submitAsyncExport(payload) {
  const submitResponseCallback = SUBMIT_ALLOWED_REJECT_STATUSES.size > 0
    ? http.expectedStatuses(202, ...SUBMIT_ALLOWED_REJECT_STATUSES)
    : undefined;
  const submitStart = Date.now();
  const res = http.post(`${BASE_URL}${EXPORT_PREFIX}/async`, JSON.stringify(payload), {
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    responseCallback: submitResponseCallback,
    tags: { flow: 'async', step: 'submit' },
  });
  const elapsed = Date.now() - submitStart;
  submitMs.add(elapsed);

  const accepted = res.status === 202;
  const rejected = SUBMIT_ALLOWED_REJECT_STATUSES.has(res.status);
  submitOkRate.add(accepted);
  submitRejectedRate.add(rejected);
  if (rejected) {
    submitRejectedCount.add(1);
  }

  const body = parseJsonResponse(res) || {};
  const taskId = body.taskId;
  const ok = check(res, SUBMIT_ALLOWED_REJECT_STATUSES.size > 0 ? {
    'async submit status is accepted/rejected-as-expected': (r) => r.status === 202 || SUBMIT_ALLOWED_REJECT_STATUSES.has(r.status),
    'async submit has taskId when accepted': () => rejected || !!taskId,
  } : {
    'async submit status is 202': (r) => r.status === 202,
    'async submit has taskId': () => !!taskId,
  });

  return { ok, accepted, rejected, taskId, submitStart, response: res };
}

function pollTask(taskId) {
  const deadline = Date.now() + MAX_POLL_SECONDS * 1000;

  while (Date.now() < deadline) {
    const pollStart = Date.now();
    const res = http.get(`${BASE_URL}${EXPORT_PREFIX}/task/${taskId}`, {
      headers: authHeaders(),
      tags: { flow: 'async', step: 'poll' },
    });
    pollRoundtripMs.add(Date.now() - pollStart);

    if (res.status !== 200) {
      sleep(POLL_INTERVAL_SECONDS);
      continue;
    }

    const body = parseJsonResponse(res);
    const status = body && body.status ? String(body.status).toUpperCase() : '';

    if (status === 'SUCCESS') {
      return { done: true, success: true, body };
    }
    if (status === 'FAILED' || status === 'CANCELED') {
      taskFailedCount.add(1);
      return { done: true, success: false, body };
    }
    sleep(POLL_INTERVAL_SECONDS);
  }

  taskTimeoutCount.add(1);
  return { done: false, success: false, body: null };
}

function downloadTask(taskId) {
  const downloadStart = Date.now();
  const res = http.get(`${BASE_URL}${EXPORT_PREFIX}/task/${taskId}/download`, {
    headers: authHeaders(),
    tags: { flow: 'async', step: 'download' },
  });
  downloadMs.add(Date.now() - downloadStart);

  const ok = check(res, {
    'download status is 200': (r) => r.status === 200,
    'download content type looks like xlsx': (r) => {
      const ct = (r.headers['Content-Type'] || '').toLowerCase();
      return ct.includes('spreadsheetml') || ct.includes('octet-stream');
    },
  });
  downloadOkRate.add(ok);
  return ok;
}

function runAsyncFlow() {
  const payload = exportRequestPayload();
  const submit = submitAsyncExport(payload);
  if (submit.rejected) {
    return;
  }
  if (!submit.ok) {
    return;
  }

  const polled = pollTask(submit.taskId);
  if (!polled.done || !polled.success) {
    taskSuccessRate.add(0);
    return;
  }

  taskSuccessRate.add(1);
  endToEndMs.add(Date.now() - submit.submitStart);

  if (DOWNLOAD_ON_SUCCESS) {
    downloadTask(submit.taskId);
  }
}

function runSyncFlow() {
  const payload = exportRequestPayload();
  payload.async = false;

  const start = Date.now();
  const res = http.post(`${BASE_URL}${EXPORT_PREFIX}/sync`, JSON.stringify(payload), {
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    tags: { flow: 'sync', step: 'sync' },
  });
  const elapsed = Date.now() - start;
  syncMs.add(elapsed);
  submitMs.add(elapsed);

  const ok = check(res, {
    'sync status is 200': (r) => r.status === 200,
    'sync content type looks like xlsx': (r) => {
      const ct = (r.headers['Content-Type'] || '').toLowerCase();
      return ct.includes('spreadsheetml') || ct.includes('octet-stream');
    },
  });
  submitOkRate.add(ok);
  taskSuccessRate.add(ok);
}

export default function () {
  if (FLOW === 'sync') {
    runSyncFlow();
  } else {
    runAsyncFlow();
  }
  sleep(THINK_TIME_SECONDS);
}

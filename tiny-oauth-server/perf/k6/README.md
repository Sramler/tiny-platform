# k6 Export Pressure Test

This directory provides a production-like HTTP pressure script for export APIs.

## File

- `export_async_flow.js`
  - `FLOW=async`: submit async task -> poll status -> download file
  - `FLOW=sync`: directly call `/export/sync`
  - auth mode:
    - `AUTH_MODE=bearer` with `ACCESS_TOKEN`
    - `AUTH_MODE=session` with form login (`/csrf` + `/login`)

## Install k6

```bash
brew install k6
```

## Quick Start

### 1) Async flow with Bearer token (recommended)

```bash
BASE_URL=http://127.0.0.1:9000 \
AUTH_MODE=bearer \
ACCESS_TOKEN='<your-jwt>' \
FLOW=async \
VUS=5 \
DURATION=3m \
TENANT_ID=1 \
k6 run tiny-oauth-server/perf/k6/export_async_flow.js
```

### 2) Async flow with session login

```bash
BASE_URL=http://127.0.0.1:9000 \
AUTH_MODE=session \
LOGIN_USERNAME=admin \
LOGIN_PASSWORD=123456 \
LOGIN_TENANT_ID=1 \
FLOW=async \
VUS=5 \
DURATION=3m \
TENANT_ID=1 \
k6 run tiny-oauth-server/perf/k6/export_async_flow.js
```

### 3) Sync flow

```bash
BASE_URL=http://127.0.0.1:9000 \
AUTH_MODE=bearer \
ACCESS_TOKEN='<your-jwt>' \
FLOW=sync \
VUS=2 \
DURATION=2m \
TENANT_ID=1 \
k6 run tiny-oauth-server/perf/k6/export_async_flow.js
```

## Common Env Vars

- `BASE_URL`: backend address, default `http://127.0.0.1:9000`
- `EXPORT_PREFIX`: export API prefix, default `/export`
- `FLOW`: `async` or `sync`, default `async`
- `AUTH_MODE`: `bearer` or `session`
- `ACCESS_TOKEN`: required for `AUTH_MODE=bearer`
- `LOGIN_USERNAME`, `LOGIN_PASSWORD`, `LOGIN_TENANT_ID`: required for `AUTH_MODE=session`
- `TENANT_ID`: export request filter tenantId, default `1`
- `PAGE_SIZE`: request page size, default `5000`
- `VUS`: concurrent virtual users, default `2`
- `DURATION`: test duration, default `1m`
- `GRACEFUL_STOP`: scenario graceful stop window, default `30s`
- `MAX_POLL_SECONDS`: async poll timeout, default `300`
- `POLL_INTERVAL_SECONDS`: async poll interval, default `2`
- `DOWNLOAD_ON_SUCCESS`: whether download async result, default `true`
- `DISCARD_RESPONSE_BODIES`: default `false`; only effective for `AUTH_MODE=bearer` + `FLOW=sync`
- `USER_AGENT`: request user-agent for device fingerprint stability, default `TinyPerfK6/1.0`
- `SUBMIT_ALLOWED_REJECT_STATUSES`: comma-separated async submit reject statuses that are considered expected, for example `429,503`
- `EXPECTED_SUBMIT_OK_RATE_MIN`: minimum allowed async submit accept rate; when `SUBMIT_ALLOWED_REJECT_STATUSES` is set, default becomes `0`
- `EXPECTED_SUBMIT_REJECT_RATE_MIN`: minimum expected reject rate, default `0`
- `EXPECTED_SUBMIT_REJECT_RATE_MAX`: maximum expected reject rate; when `SUBMIT_ALLOWED_REJECT_STATUSES` is set, default becomes `1`

## Rate Limit Scenario

When you are intentionally pressure-testing submit backpressure, allow expected reject statuses and set the reject-rate window:

```bash
BASE_URL=http://127.0.0.1:9000 \
AUTH_MODE=session \
LOGIN_USERNAME=k6bench \
LOGIN_PASSWORD=k6pass \
LOGIN_TENANT_ID=1 \
FLOW=async \
VUS=5 \
DURATION=10s \
TENANT_ID=2 \
PAGE_SIZE=5000 \
THINK_TIME_SECONDS=120 \
SUBMIT_ALLOWED_REJECT_STATUSES=429 \
EXPECTED_SUBMIT_REJECT_RATE_MIN=0.35 \
EXPECTED_SUBMIT_REJECT_RATE_MAX=0.45 \
k6 run tiny-oauth-server/perf/k6/export_async_flow.js
```

In this mode:

- submit `429/503` can be treated as expected responses instead of `http_req_failed`
- `export_submit_rejected_rate` becomes the main threshold metric for submit backpressure
- only accepted tasks continue to poll/download

## Notes

- `/export` endpoints require a valid tenant context. JWT/session must carry a valid `tenantId`.
- XLSX single-sheet row limit is `1,048,576`. For very large exports, use multi-sheet or CSV sharding.
- Script thresholds are baseline defaults. Adjust based on your SLO and environment capacity.

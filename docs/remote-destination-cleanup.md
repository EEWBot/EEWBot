# Remote destination cleanup (staging)

Companion API: [eewbot-api PR #11](https://github.com/EEWBot/eewbot-api/pull/11).
Deploy that API and confirm the existing common Access control before enabling this
feature. No production configuration, migration, or deployment is part of this change.

## Enablement and credentials

```json
{
  "database": {
    "type": "remote",
    "remote": {
      "baseUrl": "https://api-staging.eewbot.net",
      "pollIntervalMillis": 10000,
      "cleanupEnabled": true
    }
  }
}
```

`cleanupEnabled` defaults to **false**, retaining RemoteReadOnlyDestinationCleanup.
SQL mode continues using RegistryDestinationCleanup, its existing transactions,
and local Interactions. Remote mode never opens SQL or implicitly falls back to it.

Set these variables in the Bot process environment through its secret manager:

| Purpose | Client ID | Client Secret |
| --- | --- | --- |
| Both read and cleanup APIs | `EEWBOT_CF_ACCESS_CLIENT_ID` | `EEWBOT_CF_ACCESS_CLIENT_SECRET` |

No cleanup-only environment variables or Token are required. Each HTTP client
accepts injected credentials independently, allowing future authorization separation
without changing the cleanup queue/store contracts. No credentials belong in
config.json, Git, command arguments, logs or PRs. `.env`
is not loaded by the Bot. Credentials are captured at startup; rotate them in the
service environment and restart. Never enable JDK HTTP/header debugging.

When enabled, backend initialization checks both reader metadata and writer
`GET /internal/v1/cleanup/meta` for version 1 / `destinationCleanup`, before
connecting to Discord. Missing credentials, unauthorized or incompatible writer
fail startup and close acquired clients. No SQL fallback or silent read-only fallback.

The writer fixes its origin to the validated HTTPS base URL, uses only fixed paths,
never follows redirects, and attaches the common Access credentials. The existing
connect/request timeouts apply (defaults 3s/5s; maximum 60s). Request deadline
covers headers and complete body; chunked responses are limited to 64 KiB. Error
bodies and raw parser/transport causes are discarded.

## Operations and immutable delivery targets

| Cleanup method | POST path under `/internal/v1/cleanup` | Body |
| --- | --- | --- |
| `guildDeleted` | `/guild` | `guildId` decimal string |
| `destinationDeleted`, `destinationsUnavailable` | `/destinations` | `targetIds` string array |
| `webhooksUnavailable(Map<Long, DeliveryTarget>)` | `/webhooks` | `targets` containing `targetId` and `expectedWebhookId` strings |
| `webhookUrlsUnavailable` | `/webhook-ids` | `webhookIds` string array |

EEWService passes only direct webhook sends that returned **404**, with the
immutable DeliveryTarget actually used for that send. The remote adapter extracts
its webhook ID and sends no URL or webhook token. D1 compares target and expected
ID, so a delayed failure does not clear a different replacement webhook. The old
Collection<Long> overload retains SQL semantics through the default adapter; in
remote mode it warns and refuses an unguarded webhook clear.

Webhook Sender URL notifications are converted to deduplicated webhook IDs, ignoring
thread query differences. Invalid references are skipped with fixed count-only
warnings. Java keeps the existing signed-long ID range (1 through Long.MAX_VALUE),
uses decimal strings without floating point, and never sends invalid/overflowing IDs.
The API itself supports the full unsigned 64-bit range.

Each request includes at most 500 targets (larger observations split into bounded
requests). API success must be HTTP 200 with a single nonnegative integer `affected`.
No response updates the in-memory snapshot. Existing RevisionPoller detects D1
revision changes, reloads and validates the whole snapshot, then atomically publishes
it. Unchanged revision causes no snapshot fetch. Failures keep the old snapshot and
delivery continues from memory.

## Queue, failure and shutdown guarantees

Runtime requests use one dedicated worker, **one request in flight** and **256
queued requests**, each with at most 500 targets. Gateway/deleted/unavailable
callbacks only enqueue work. Rejection and failed requests produce warnings;
success counts are logged only after successful HTTP responses.

**A failed write is never automatically replayed.** Timeout, connection loss,
503 or malformed success can mean D1 already committed. Later independent queued
operations wait with exponential jittered backoff (0.5–1s initially, capped at
30–60s). 401/403 impose 60s between attempts of later operations. Other non-200
statuses, including 405/429/503, fail and back off. The worker sleeps; Gateway and
delivery threads do not. This is pacing of distinct notifications, not a retry of
the failed one. Failed notifications are logged and dropped, with no durable retry.

`webhookUrlsUnavailable` keeps its synchronous **actual affected-row count** by
submitting to the same worker and waiting up to **10s per 500-ID batch**, including
queue time/backoff. In production its only caller is Webhook Sender maintenance,
scheduled every 15s by GatewayManager; no Gateway event callback calls it. Rejection,
timeout/interruption or HTTP failure raises a sanitized RemoteConfigException,
which existing maintenance code catches. A timeout cancels pending work; an active
write may already have committed. It never returns a fabricated success count.
If an earlier batch succeeded and a later batch failed, the method throws instead
of claiming a complete total. Avoid calling this method from a cleanup worker or
Gateway event loop.

At shutdown, listeners stop before `save()` closes the queue. It waits up to **10s**,
warns about unfinished active/queued operations, cancels pending futures, interrupts
the worker and shuts down its HttpClient. Backend.close also closes cleanup, so
startup failure and repeated close release resources safely.

The queue is **not persistent**. Crash/restart, queue overflow, shutdown deadlines
and failed HTTP requests can lose cleanup notifications. Reapplying against unchanged
D1 is a no-op, but there are no durable idempotency keys or ordering guarantees:
a delayed delete can remove a recreated target/guild. Expected webhook IDs protect
replacement by a *different* ID; reuse of the same ID still has an ABA race. Operators
must reconcile failures; do not blindly replay deletion logs.

## Staging operations and validation

Use the existing internal Access application protecting
`api-staging.eewbot.net/internal/*`, its Service Auth policy/Service Token, and the
staging Worker `ACCESS_AUD`. The common token intentionally authorizes both reads
and cleanup writes for the current Core-only usage. The Worker trusts native
Access context only and denies missing/mismatched common configuration. No Access
Application/AUD/Token split is added. Future Discord commands must use Discord
signature and user permissions instead of this Service Token. See the API PR's
`docs/cleanup-api.md` for setup and D1 guarantees.

Run local verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
.\gradlew.bat test integrationTest build --console=plain
```

After both common credentials are securely set in the current process, opt into the
live Java check (reader/cleanup meta, empty D1 operations and unchanged revision).
Changed-row tests additionally require the verified fixture prepared by the API
repository's `scripts/smoke-cleanup-staging.mjs --prepare-java`. Pass its nonsecret
`.wrangler/cleanup-staging-fixture.json` as `EEWBOT_REMOTE_CLEANUP_STAGING_FIXTURE`.
The test validates the reserved IDs/marker/disabled delivery flags before writing.
It never logs in to Discord or opens a local database:

```powershell
$env:EEWBOT_REMOTE_CLEANUP_STAGING_SMOKE='true'
.\gradlew.bat integrationTest --tests '*RemoteCleanupStagingSmokeTest' --console=plain
Remove-Item Env:EEWBOT_REMOTE_CLEANUP_STAGING_SMOKE
```

Always run API `scripts/smoke-cleanup-staging.mjs --finish-java` afterward to remove
the verified synthetic survivors and compare original destination contents.

For actual changed-row validation, first insert unique synthetic destinations through
an explicit staging-only D1 maintenance operation after checking the IDs are absent.
Exercise guild/channel/thread/bulk deletion, guarded webhook replacement and shared
webhook-ID clearing. Verify affected counts, one revision increment per operation,
no increment for replay, and eventual Java poll reload. Remove only synthetic
survivors and **never reset the revision**. No generic fixture-insertion HTTP route
is exposed.

Only then stop the identified staging Bot, back up config/JAR/SQL as described in
`remote-destination-storage.md`, provide the common credential pair, enable cleanup and
restart. Confirm compatibility checks and revision polling, then a synthetic cleanup
event. Rollback is `cleanupEnabled:false` plus restart (remote reads remain active).
Do not change production or claim Bot cutover without access to its actual host.

### Status on 2026-10-26

The implementation follows the updated common Access specification. No new
Application/AUD/Token or cleanup environment variables are required. Worker version
`cada8f76-1728-4d58-b23c-5dd244daf542` uses the existing shared Token/AUD. The same
Token obtained cleanup metadata and snapshots with HTTP 200. Anonymous/forged-header
cleanup attempts were denied without changing D1.

API live smoke exercised 11 changed/no-op operations (revision 0 → 6). Seven
synthetic Java destinations were then inserted (revision 7). The live Java suite
passed **3 tests**: metadata/common headers, no-op counts, queued guild/channel/thread
deletion, guarded webhook clearing, shared webhook-ID clearing and real RevisionPoller
publication. Java operations advanced revision **7 → 12**. The initial Snapshot stayed
unchanged until polling published revision 12. The existing reader smoke also
confirmed a real 10-second poll at nonzero revision.

Final fixture removal advanced revision to **13**, with an empty snapshot. The
maintenance script's digest comparison confirmed that all original destination
contents were preserved exactly. Synthetic survivors were removed; revision was
not reset. Existing public health/Interactions-denial and authenticated read smokes
passed afterward. Secret values, webhook tokens and snapshots were not printed.

Bot host/service has not been identified; API/client tests do not establish actual
Bot cutover. The remaining operator task is to deploy the built JAR to that staging
host, retain its common credentials, set `cleanupEnabled:true`, restart, and verify
real Gateway/Sender events. No production resources or Access policies were changed.

`gradlew test integrationTest build` passed: unit **671 total / 670 passed / 1 skipped**,
integration **220 total / 205 passed / 15 skipped**, zero failures. Skips comprise
the explicit golden generator, Docker-dependent PostgreSQL tests, unconfigured
external Discord tests and three opt-in staging cases. The 43 RemoteCleanupTest
cases plus four EEWService 404/status cases and one SQL adapter compatibility case
all passed. Local API tests separately verify stale-webhook safety and D1 rollback.
The three opt-in cases passed separately against staging using the existing `.env`
common token in process memory only. This does not substitute for actual Bot cutover.

# Remote destination cleanup (staging)

Companion API: [eewbot-api PR #11](https://github.com/EEWBot/eewbot-api/pull/11).
Deploy that API and configure its separate Access writer before enabling this
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
| Read API | `EEWBOT_CF_ACCESS_CLIENT_ID` | `EEWBOT_CF_ACCESS_CLIENT_SECRET` |
| Cleanup API | `EEWBOT_CF_ACCESS_CLEANUP_CLIENT_ID` | `EEWBOT_CF_ACCESS_CLEANUP_CLIENT_SECRET` |

No credentials belong in config.json, Git, command arguments, logs or PRs. `.env`
is not loaded by the Bot. Credentials are captured at startup; rotate them in the
service environment and restart. Never enable JDK HTTP/header debugging.

When enabled, backend initialization checks both reader metadata and writer
`GET /internal/v1/cleanup/meta` for version 1 / `destinationCleanup`, before
connecting to Discord. Missing credentials, unauthorized or incompatible writer
fail startup and close acquired clients. No SQL fallback or silent read-only fallback.

The writer fixes its origin to the validated HTTPS base URL, uses only fixed paths,
never follows redirects, and attaches only the cleanup credentials. The existing
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

Create **EEWBot staging cleanup writer** protecting both
`api-staging.eewbot.net/internal/v1/cleanup` and `/internal/v1/cleanup/*`. Its Service
Auth policy includes only **eewbot-core-staging-cleanup**. Bind its AUD to staging
Worker `ACCESS_CLEANUP_AUD`, distinct from reader `ACCESS_AUD`. The Worker trusts
native Access context only. Missing configuration fails closed, and read credentials
cannot write. See the API PR's `docs/cleanup-api.md` for full setup and D1 guarantees.

Run local verification:

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
.\gradlew.bat test integrationTest build --console=plain
```

After all four credentials are securely set in the current process, opt into the
live Java check (writer meta, audience separation, empty D1 operations and unchanged
revision). It never logs in to Discord or opens a local database:

```powershell
$env:EEWBOT_REMOTE_CLEANUP_STAGING_SMOKE='true'
.\gradlew.bat integrationTest --tests '*RemoteCleanupStagingSmokeTest' --console=plain
Remove-Item Env:EEWBOT_REMOTE_CLEANUP_STAGING_SMOKE
```

For actual changed-row validation, first insert unique synthetic destinations through
an explicit staging-only D1 maintenance operation after checking the IDs are absent.
Exercise guild/channel/thread/bulk deletion, guarded webhook replacement and shared
webhook-ID clearing. Verify affected counts, one revision increment per operation,
no increment for replay, and eventual Java poll reload. Remove only synthetic
survivors and **never reset the revision**. No generic fixture-insertion HTTP route
is exposed.

Only then stop the identified staging Bot, back up config/JAR/SQL as described in
`remote-destination-storage.md`, provide both credential pairs, enable cleanup and
restart. Confirm compatibility checks and revision polling, then a synthetic cleanup
event. Rollback is `cleanupEnabled:false` plus restart (remote reads remain active).
Do not change production or claim Bot cutover without access to its actual host.

### Status on 2026-10-26

API staging Worker deployment succeeded (version
`99c8324b-608a-4123-9a68-db60cc88d3ce`). Existing read token returned 200 for meta,
revision and empty snapshot (revision 0). Cleanup meta and POST returned 503 with
the writer AUD unset; no staging data was modified by those requests.

The existing Wrangler OAuth cannot create Access Service Tokens (HTTP 403).
Browser app-control approval timed out. Writer Application/token/AUD provisioning,
live changed-row D1 writes, Java writer smoke, and actual staging Bot enablement
remain pending. Bot host/service has not been identified. Local tests and a deployed
fail-closed route do not establish that these operational steps are complete.

`gradlew test integrationTest build` passed: unit **671 total / 670 passed / 1 skipped**,
integration **219 total / 205 passed / 14 skipped**, zero failures. Skips comprise
the explicit golden generator, Docker-dependent PostgreSQL tests, unconfigured
external Discord tests and two opt-in staging smoke tests. The 43 RemoteCleanupTest
cases plus four EEWService 404/status cases and one SQL adapter compatibility case
all passed. Local API tests separately verify stale-webhook safety and D1 rollback.
The existing read-only RemoteStagingSmokeTest was also explicitly run against the
deployed staging Worker and passed (1 test), including a real 10-second revision
poll. This used the existing `.env` read token in process memory only; it does not
substitute for the pending writer smoke or Bot cutover.

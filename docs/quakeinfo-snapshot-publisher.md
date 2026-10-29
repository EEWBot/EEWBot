# QuakeInfo snapshot publisher

This opt-in publisher pushes the exact latest `QuakeInfoStore.getLatestReport()`
result to the Workers snapshot API (PR A, EEWBot/eewbot-api#25). It works with
SQLite, PostgreSQL and remote destination storage independently.

```json
{
  "quakeInfoPublisher": {
    "enabled": false,
    "baseUrl": "https://api-staging.eewbot.net",
    "connectTimeoutMillis": 3000,
    "requestTimeoutMillis": 5000,
    "reconcileIntervalMillis": 60000,
    "retryInitialMillis": 1000,
    "retryMaxMillis": 60000
  }
}
```

Preserve the rest of config.json. Default is disabled, which creates no publisher
or HTTP requests. The origin must be HTTPS without userinfo, path, query or fragment.
Literal loopback HTTP is available only through package-local test constructors.
Timeouts are 1–60000ms, reconciliation 10–3600000ms, retries 10–3600000ms with
maximum >= initial. The repository currently builds Java 21 sources with Java 25
in CI; this change preserves that existing toolchain.

The **existing common Cloudflare Access Service Token is shared** with Remote
Database access. Read `EEWBOT_CF_ACCESS_CLIENT_ID` and
`EEWBOT_CF_ACCESS_CLIENT_SECRET` only from the process environment, and send
`CF-Access-Client-Id` / `CF-Access-Client-Secret`. No new Service Token, Access AUD,
Bearer credential or custom JWT verification is needed. Never store credentials
in JSON, D1, logs, CI output or PR descriptions. Missing/invalid credentials for
an enabled publisher fail configuration initialization with a static message.
Network/push failures happen asynchronously and do not interrupt ordinary delivery.

## Rendering and triggers

`QuakeInfoSnapshot` calls the existing `QuakeInfo.createEmbeds()`,
`I18nEmbedBuilder`, `EmbedPacker.pack()` and `EmbedRenderer.toDiscordEmbeds()`.
I18n maps existing locale directories ja_JP/en_US/zh_TW/ko_Hanja/ko_KP to API codes
ja_jp/en_us/zh_tw/ko_hanja/ko_kp. RendererQueryFactory stays in Java; generated
image URLs are preserved, and the Renderer HMAC key never goes to Workers/D1.

Every packed message is serialized as a separate embed array. Five languages
are **never stored in one D1 row**; each language can occupy multiple rows,
preserving order and boundaries even when message counts differ between languages.
Actual UTF-8 limits are 64KiB/message, 512KiB/language and 2MiB/snapshot; at most
16 messages/language. EmbedPacker applies the existing Discord limits. Any language
or limit failure aborts rendering before uploading an incomplete snapshot.

Store writes notify listeners **outside** the Store lock, including long-feed
reference loads and older VXSE51/VXSE53 reports used by VXSE52/VXSE61. The original
latest selection logic is unchanged. Store version is checked before/after the
complete five-language render; a concurrent reference change discards that render.
The long-feed gateway also signals after initial loading finishes. Publisher
start requests synchronization, and periodic reconciliation repairs missed signals
or missing remote state. Unchanged source and rendered-content fingerprints avoid
unnecessary snapshots. Updates during work are coalesced into one pending flag.
No HTTP request or full Embed generation runs under the Store lock.

## Ordering and retries

One scheduled executor owns boot UUID, epoch, monotonic sequence and immutable
pending snapshot. Workers issues an idempotent lease per boot UUID. An old process
fenced by a newer epoch stops publishing and **never reacquires an epoch**. Only
the intended active Core should be restarted. UUIDs must not be reused across boots.
Workers keeps lease retry receipts for seven days.

Source order is `(reportDateTime, dateTime, parsed int serial, infoType priority)`;
null dates are oldest and invalid int serial is -1. Priorities are announcement 0,
correction 1, cancellation 2. A newer generation cannot replace a newer source
with an older source. For a tied source identity, a greater epoch/sequence supports
rerendering after new reference intensity/image information. Different source
identities tied on ordering remain first-accepted, matching Store behavior.

Create metadata/expected manifests, PUT every language/message, then POST commit.
Only a complete validated snapshot is published atomically. Requests use parameter-
validated paths, finite connection/full-response timeouts, no redirects, and a
32KiB response cap. Error diagnostics are static or allowlisted codes only.

Retries use capped exponential backoff; new signals cannot bypass backoff.
The same pending object retains its ID, serialized metadata, bytes and hashes.
A newer local state replaces a failed intermediate snapshot with a new sequence.
After an uncertain Commit (including timeout after successful publication), read
latest/meta before deciding whether to retry. A successful remote ID is accepted;
an already-newer source conflict is remembered until local content/source changes,
avoiding unlimited rejected snapshots. Periodic reconciliation checks that the
last published ID/hash still exists. GatewayManager closes the publisher before
other executors, removes its listener, interrupts HTTP and waits up to five seconds.

Workers reads saved embeds for `/quakeinfo`; it never asks Core at command time.
Core can stop while the last committed snapshot remains available. Publication,
read and snapshot cleanup do not modify `config_meta.channels_revision`; the
independent `quake_latest.quake_revision` tracks earthquake data.

## Verification

Run `./gradlew test` and `./gradlew shadowJar`. Unit tests reuse existing VXSE51,
52,53,61 XML, verify five languages, exact packing, images, corrections/cancellations,
reference enrichment, initialization from long feed, version races, failures,
idempotent retry, ambiguous commits, reconciliation and fencing. Local HttpServer
tests cover Access headers, redirects, slow/oversized responses and sanitization.

The API repository's `npm run test:java` adds real Java → loopback Workers/D1
HTTP integration. Set `EEWBOT_CORE_DIR` to this checkout after building shadowJar.
It uses synthetic Access and Renderer secrets, marked historical TEST fixtures,
and requires no real Cloudflare/Discord credentials or external network.
PR C extends this pipeline with signed `/quakeinfo` interactions. Stage migration
and API deployment precede publisher validation; no production deployment is part
of this work. Record actual Access/Discord staging checks separately from local tests.

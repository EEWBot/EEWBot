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
    "retryInitialMillis": 1000,
    "retryMaxMillis": 60000
  }
}
```

Preserve the rest of config.json. Default is disabled, which creates no publisher
or HTTP requests. The origin must be HTTPS without userinfo, path, query or fragment.
Literal loopback HTTP is available only through package-local test constructors.
Timeouts are 1–60000ms, retries 10–3600000ms with
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

Every packed message is serialized as one embed array (1–10 embed objects).
Actual UTF-8 limits are 64KiB/message, 512KiB/language and 2MiB/snapshot; at most
16 messages/language. EmbedPacker applies the existing Discord limits; Workers
validates embeds only shallowly, so these limits are Core's responsibility. Any
language or limit failure aborts rendering before anything is sent.

Store writes notify listeners **outside** the Store lock, including long-feed
reference loads and older VXSE51/VXSE53 reports used by VXSE52/VXSE61. The original
latest selection logic is unchanged. Store version is checked before/after the
complete five-language render; a concurrent reference change discards that render.
The long-feed gateway also signals after initial loading finishes. Publisher
start requests synchronization. There is no periodic resend: Core publishes only
on start and on Store updates, and a failed send stays pending until it succeeds.
An unchanged body fingerprint avoids resending identical content. Updates during work are coalesced into one pending flag.
No HTTP request or full Embed generation runs under the Store lock.

## Request, ordering and retries

Core runs as a single instance, so there are no leases, epochs, sequences,
snapshot IDs, manifests or fencing. Publication is one request:

```
PUT /internal/v2/quakeinfo/latest
{"eventId", "reportType", "reportDateTime"|null, "dateTime"|null, "serial",
 "infoType", "languages": {"ja_jp": [[embed, ...], ...], ...all five...}}
→ 200 {"accepted": true|false, "revision": n}
```

Workers orders by `(reportDateTime ?? "", dateTime ?? "", serial, infoType priority)`
(発表 0, 訂正 1, 取消 2; the priority is derived from `infoType`). Invalid int serial
is -1. A strictly newer stored report yields `accepted: false`; an equal key replaces
the stored one, so resending identical bytes is idempotent.

Requests use a fixed path, finite connection/full-response timeouts, no redirects,
and a 32KiB response cap. Error diagnostics are static or allowlisted codes read
from `{"error":{"code":"..."}}`. Only 5xx and transport failures are retried, with
capped exponential backoff that new signals cannot bypass; a retry resends the
same body. A 4xx is remembered for that body fingerprint and not retried until the
rendered content changes. A reset remote state is repopulated on the next Core
start or quake update. GatewayManager closes the publisher before other
executors, removes its listener, interrupts HTTP and waits up to five seconds.

Workers reads saved embeds for `/quakeinfo`; it never asks Core at command time.
Core can stop while the last committed snapshot remains available. Publication,
read and snapshot cleanup do not modify `config_meta.channels_revision`; the
independent `quake_latest.quake_revision` tracks earthquake data.

## Verification

Run `./gradlew test` and `./gradlew shadowJar`. Unit tests reuse existing VXSE51,
52,53,61 XML, verify five languages, exact packing, images, corrections/cancellations,
reference enrichment, initialization from long feed, version races, transient
failures resending identical bytes, non-retried 4xx and no periodic resend. Local HttpServer
tests cover Access headers, redirects, slow/oversized responses and sanitization.

The API repository's `npm run test:java` adds real Java → loopback Workers/D1
HTTP integration. Set `EEWBOT_CORE_DIR` to this checkout after building shadowJar.
It uses synthetic Access and Renderer secrets, marked historical TEST fixtures,
and requires no real Cloudflare/Discord credentials or external network.
PR C extends this pipeline with signed `/quakeinfo` interactions. Stage migration
and API deployment precede publisher validation; no production deployment is part
of this work. Record actual Access/Discord staging checks separately from local tests.

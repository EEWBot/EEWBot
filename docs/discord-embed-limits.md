# Discord embed webhook limits

Measured on 2026-10-19 (JST) using the test webhook in `.env` and Execute Webhook
with `wait=true`. No webhook URL or token is recorded here.

References: [discord-api-docs #8554](https://github.com/discord/discord-api-docs/issues/8554),
[documented embed limits](https://docs.discord.com/developers/resources/message#embed-limits),
and `feat/componentsv2`'s `WebhookEffectiveCostEstimator` (read at commit `450b2fce`).

## Measurements

The table gives the maximum **decoded UTF-8 description filler** in a successful
request. Every next byte returned HTTP 413 / code 40005 in this run, rather than
the HTTP 500 reported in #8554. These values depend on the webhook identity;
they are not universal Discord limits. Unless stated otherwise, color and
webhook username/avatar overrides were absent.

| Other payload data | Maximum filler bytes |
| --- | ---: |
| None | 10143 |
| One field: name `n`, value `v`, inline false | 10130 |
| Two such fields | 10117 |
| 25 such fields | 9818 |
| Second embed with description `v` | 10114 |
| Nine more embeds with description `v` | 9882 |
| Color 0 / 16711680 | 10141 / 10136 |
| Timestamp with seconds / nanoseconds | 10135 / 10130 |
| Title `title` | 10134 |
| Footer text `footer` / author name `author` | 10132 / 10133 |
| Embed URL `https://example.com/a` | 10118 |
| Image / thumbnail `https://example.com/a.png` | 10112 |
| Footer text `footer` and that icon URL | 10100 |
| Author `author`, example.com/a URL and a.png icon | 10077 |
| Field: 127-byte name/value, inline false | 9875 |
| Field: 128-byte name/value, inline true | 9869 |
| Field: 256-byte name, 3072-byte value, inline false | 6799 |
| Resolved image / thumbnail [avatar PNG](https://cdn.discordapp.com/embed/avatars/0.png) | 9997 |
| Footer `footer` and resolved icon | 10079 |
| Author `author` and resolved icon | 10081 |
| Webhook username `x` | 10152 |
| Webhook username 80 four-byte emoji | 9830 |
| Resolved webhook avatar override | 10107 |
| 80-emoji username and resolved avatar override | 9794 |

The baseline request was 10174 raw JSON bytes. Escaping the same successful
description as `\uXXXX` produced **20317 raw bytes and still succeeded**.
Quotes, backslashes, HTML characters, newlines and tabs also consumed their
decoded lengths, rather than their JSON escape lengths. Thus the former
9000-byte raw JSON cap and Gson escape accounting were inappropriate.

An image/thumbnail resolving successfully added 96 relative to the same
accounting for an unresolved URL. Icons and webhook avatars have distinct costs;
the resolved webhook avatar reduced the baseline budget by 36. These are empirical
differences, not proof of a particular internal serialization format.

## Accounting used by the application

`EmbedWebhookCost` uses an empirical scale with budget **10172**. On this scale,
a bare embed has cost 24 and a long description has cost `UTF8(text) + 5`:
`10143 + 24 + 5 = 10172`. This normalization is specific to the embed measurements;
it must not be confused with the Components V2 estimator's 10170 scale, or a
documented 10 KiB limit.

The implementation deliberately uses conservative costs rather than exact boundary
predictions: 5 per string, 6 per field plus 4 for inline true, 7 for color, and
14 for a timestamp. Footer and media wrappers have additional costs. The measured
long field needed 16 beyond its text, covered by `6 + 5 + 5`. Measured RGB costs
ranged from 2 to 7, and timestamp costs from 8 to 13.

Every message additionally reserves 320 for the destination webhook's unknown
display name (80 four-byte code points) and 128 for its avatar. Every image,
thumbnail or icon reserves another 128 for resolution-dependent metadata, above
its URL and wrapper cost. These allowances cover the measured variations with
some room remaining; arbitrary external media and future API changes are not
fully characterized. A webhook with a short name can accept more than the packer
chooses to send.

Character limits remain separate: at most 10 embeds/message, 25 fields/embed,
4096 code points/description, 1024/value and 6000 combined embed text/message.
Description and field values are split without ellipses or lost newlines,
preferring newline boundaries and preserving surrogate pairs. Title/link/thumbnail
appear first; author/footer/image/timestamp appear last, potentially on a separate
embed. The existing per-property truncation of titles, field names, footer text and
author names, overlong-URL removal and decoration-URL removal priority remain.

Send-time diagnostics use the same estimator as the packer. Raw Gson JSON size
does not trigger the old misleading error log.

## Reproduction and regression tests

Run `python scripts/measure_discord_embeds.py base fields1 imageResolved usernameLongAvatar`
from the repository root. It reads `.env`, posts test messages, handles 429, verifies
both sides of each one-byte boundary and also tests escaped JSON. Output is limited
to variant names, sizes and statuses, and saved under ignored `build/embed-measurement/`.
The webhook's default identity affects results. Media resolution can also change.

Run ordinary regressions with `.\gradlew.bat test`.
For live generated-payload tests, export only `DISCORD_WEBHOOK_URL` from `.env`, then
run `.\gradlew.bat integrationTest --tests '*EmbedWebhookIntegrationTest'`.
The integration test is opt-in and excluded from ordinary `test`. On this run,
all 17 generated messages were accepted, including long Japanese descriptions,
maximum-length fields, inline fields, emoji, continuation fields, escaped text,
resolved media, a nanosecond timestamp and an 80-emoji webhook username.

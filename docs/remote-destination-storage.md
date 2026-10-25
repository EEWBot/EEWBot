# Cloudflare D1 Remote Backend（ステージング）

`database.type=remote` はCloudflare Workersの読取専用Internal APIを通じて
D1の配信設定を参照する。ローカルSQLite/PostgreSQLの初期化、Flyway migration、
設定の自動コピーは行わない。既定値は引き続き `sqlite`。

## 構成と通信の境界

```text
EEWBotFactory → RemoteDestinationBackend
                ├─ RemoteConfigApiClient（標準java.net.http.HttpClient）
                ├─ RemoteDestinationConfigSource
                └─ 専用Revisionポーリングscheduler
起動 → GET meta → 互換性確認 → GET snapshot → initializeSnapshot
     → RevisionPoller（固定遅延10秒）→ GET revision
       → 変更時だけSnapshotDeliveryRegistryの専用executorでGET snapshot
       → 全件検証 → immutable DeliverySnapshot → AtomicReference交換
配信 → メモリ上のSnapshotだけを検索・判定（HTTPアクセスなし）
```

SQLモードは既存のbackend、管理レジストリ、2秒ポーリングを利用する。
Remoteではポーリング専用スレッドを使い、DMData等の共有schedulerを占有しない。
初回読取は同期処理で、metaまたはSnapshotの取得・検証失敗はDiscord接続前に起動を中止する。
Revision 0・配信先0件は正常で、この場合は通常のチャンネル配信が発生しない。

BackendがHTTP client、そのexecutor、ポーリングschedulerを所有する。
初期化失敗・後続のBot起動失敗・通常終了で解放し、複数回closeしても安全。
JDKは既存ビルド設定のJava 21以上（CIは25）を使用する。
HTTPの終了には[標準HttpClientのshutdownNow](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html#shutdownNow())を使う。

## 設定

既存 `config.json` のdatabaseセクションを次のように設定する。
接続先の既定URLはない。ステージングでは以下のURLを明示する。

```json
{
  "database": {
    "type": "remote",
    "remote": {
      "baseUrl": "https://api-staging.eewbot.net",
      "pollIntervalMillis": 10000,
      "connectTimeoutMillis": 3000,
      "requestTimeoutMillis": 5000,
      "maxSnapshotBytes": 33554432
    }
  }
}
```

`pollIntervalMillis` は1〜3,600,000ms、両timeoutは1〜60,000ms。
`maxSnapshotBytes` は正のintで、既定32MiB。大規模Snapshotに合わせて調整できる。
meta/revisionは各64KiBまで。Content-Lengthのないchunkedレスポンスも制限する。
JSONのネスト上限32、単一文字列上限8192文字。設定を巨大化するとパース後のメモリも
増えるため、JVMのヒープ容量に合わせる。request timeoutは接続・ヘッダー・本文完了までを含む。

Bot起動プロセスに次の環境変数を安全に渡す。

```text
EEWBOT_CF_ACCESS_CLIENT_ID
EEWBOT_CF_ACCESS_CLIENT_SECRET
```

設定JSONに認証情報のフィールドはなく、環境変数が未設定・空・空白・不正なヘッダー値なら
起動できない。`.env` の自動読込は実装していない。サービスマネージャーやコンテナの
secret/environment設定を使用する。既存の手元の `.env` に `API_CLIENT_ID` /
`API_CLIENT_SECRET` がある場合は、起動プロセス内で上記の名前にマッピングする。
認証情報の内容を標準出力へ出したり、コマンド引数・PR・履歴・ログへ含めたりしない。

## HTTP API v1の契約

| GETの固定パス | 必須データ |
| --- | --- |
| `/internal/v1/meta` | `apiVersion: 1`、capabilitiesに `configRevision` と `deliverySnapshot` |
| `/internal/v1/config/revision` | `revision` |
| `/internal/v1/config/snapshot` | `revision` と `destinations` 配列 |

各リクエストに `CF-Access-Client-Id` / `CF-Access-Client-Secret` を付与する。
HTTPSを必須とし、origin以外のpath、userinfo、query、fragmentをbaseUrlで拒否する。
自動redirectは無効で、302等は失敗。認証ヘッダーを別ホストへ転送しない。
Cookie handler・認証JWTの永続化・HTTPキャッシュを設けず、リクエストはno-store。
HTTPライブラリのヘッダー／wire debugログ（`jdk.httpclient.HttpClient.log` 等）を有効にしない。
ローカルテストだけpackage-privateコンストラクターで数値loopback HTTPを許可する。

RevisionはJSON整数の0〜9,007,199,254,740,991。文字列、小数、指数表記、範囲外値を拒否する。
JSONは重複キー・末尾データ・不正JSONも拒否する。
Snapshot API自身のrevisionを保持し、別のrevision取得結果で上書きしない。

Destinationの全フィールドを必須とし、nullable項目も明示的なnullを要求する。
targetId/channelId/threadId/guildId/webhookIdはdecimal文字列から直接long/Longに変換する。
浮動小数点を経由せず、NULLを維持する。配信フラグ5個はJSON boolean、minIntensityは
0〜9の整数からSeismicIntensityへ変換する。langは文字列。
targetIdの重複、型の不一致、欠落、不正値が1件でもあれば全体を拒否する。

**Snowflakeの制限:** APIは符号なし64bitを表現できるが、既存ChannelWebhook.id()や
SQL等の符号付きlong経路との互換性のため、Java Remoteは1〜Long.MAX_VALUEの
正規decimal文字列だけを受け入れる。Long.MAX_VALUE超はSnapshot全体を拒否し、
別のIDへオーバーフローさせない。URL内のWebhook/thread IDにも同じ制限を適用する。

webhookUrl=nullの場合はwebhookIdもnull。URLがある場合はDiscordのWebhook URL形式と
webhookIdの一致を検証する。保存済みURLを `?thread_id=` や他のqueryも含めて保持する。
thread_idの重複・不正値、他ホストやfragmentを含むWebhook URLは拒否する。
例外・ログには入力データや原因例外を添付しない。

## 障害・更新・巻き戻り

稼働中のHTTP 401/403/429/5xx、timeout、JSON/データ検証失敗は最後に正常取得した
Snapshotを保持し、空Snapshotへの置換は行わない。ログは固定の診断文とHTTP status等だけ。
配信は保持済みSnapshotで続ける。

HTTP/通信/JSON構文障害はendpointごとに指数バックオフを適用する。
最初は0.5〜1秒、その後倍増し30〜60秒までのjitter付き待機とする。
待機中の呼出しは通信せず失敗し、スレッド内でsleepや即時再試行を行わない。
実際の再試行は後続のポーリングで行う。認証401/403は60秒抑制し、再認証ループを作らない。
初回の失敗は待って再試行せず起動を失敗させる。
構文が正しくてもデータが不正なSnapshotは全体を拒否し、次回pollで再検証する。
HTTPアクセスはSource/transport内で直列化し、同時リクエスト数を1に制限する。
再読込要求は既存registryで集約し、取得失敗直後の即時reload再帰を行わない。
正常reload後のrevision再確認と追加reloadは維持し、更新終了後に最新へ収束する。

revision応答が直前の成功pollまたは採用済みSnapshotより小さい場合、またはSnapshotの
revisionが前回成功Snapshotより小さい場合、巻き戻りとしてラッチする。
旧Snapshotを保持し、以降のRemote読取を停止して**運用者による再起動が必要**と報告する。
既存registryの単調増加比較を変更せず、古い設定へ黙って固定されることを防ぐ。
復元されたD1の状態とURLを確認してからBotを再起動すると、Revision 0も再受入できる。

## 管理操作とCleanup

RemoteモードではSlashCommandContext/SlashCommandHandlerを生成せず、コマンド登録と
ApplicationCommand/SelectMenu/Button購読を開始しない。Discord上に既存のコマンド定義が
残っていても削除しない。この段階では管理コマンドは応答できない。
SQLモードのLocal InteractionsとRegistryDestinationCleanupは従来どおり。

Guild/Channel/Thread削除、配信不可、Webhook無効化、senderのnot-found通知は
RemoteReadOnlyDestinationCleanupへ送る。D1もローカルDBも変更せず、種別・件数だけを
`Remote cleanup NOT applied` と警告する。URL一括無効化の返値は0件。
終了時saveはno-op。成功した設定変更として報告しない。
無効な配信先が次の配信時にも残り得るため、ステージング用の暫定措置。
現時点でD1を変更する管理操作はJavaに実装されていない。

## ステージング切り替え手順

1. 対象がステージングBot・Discord Applicationであることを確認する。
   実行中のサービス名、config/data directory、使用中JAR、起動環境の設定方法を特定する。
   `.env` にAPIのService Tokenだけがあっても、Botの操作先を特定したことにはならない。
2. API側 [PR #9](https://github.com/EEWBot/eewbot-api/pull/9) がmasterへマージされていることと
   staging Worker `eewbot-api-staging` がそのAPIを公開していることを確認する。
   2026-10-26時点で同PRのmergeを確認済み。Java PRからAPI側のmerge/deployは行わない。
3. ステージングBotを停止し、設定・JAR・ローカルDBをアクセス制限した場所へバックアップする。
   SQLiteは全接続の停止後にDBをコピーする。WAL使用時はcheckpoint済みを確認するか、
   DBと残存する `-wal` / `-shm` を同じ停止状態でまとめて保存する。
   PostgreSQLなら既存のバックアップ手順を使う。既存DBを削除・移行しない。
4. ステージング専用Service Tokenを実行環境の上記2変数へ設定する。
   本番用Token、Discord Application、Worker、API URLを使用しない。
5. ステージングのconfigだけを `type=remote` / `baseUrl=https://api-staging.eewbot.net` へ変更し、
   新しいJARで再起動する。
6. `Delivery snapshot initialized: 0 channels, revision 0`、Local Interactions無効、
   `RevisionPoller started with interval 10000 ms` を確認する。
   通常ログへSnapshot全文・ヘッダー・Webhook URLを出さない。配信先0件は正常。
7. ステージングの管理下でAPI接続を一時的に遮断し、旧Snapshot保持と復旧後の収束を確認する。
   自動テストではloopbackで障害を再現済み。共有Worker/Accessポリシーを障害試験のために変更しない。

実Service TokenでJava HTTP/変換/RevisionPollerだけを検証する補助手順も用意した。
BotのDiscord起動、ローカルDB操作、Worker変更は行わず、ステージング固定URLへの読取だけ。

```powershell
# 認証情報はsecret管理から環境変数へ既に設定されている前提
$env:EEWBOT_REMOTE_STAGING_SMOKE='true'
.\gradlew.bat integrationTest --tests '*RemoteStagingSmokeTest' --console=plain
Remove-Item Env:EEWBOT_REMOTE_STAGING_SMOKE
```

通常のテストは実Service Token/Cloudflare接続を必要とせず、外部smokeは明示opt-in時だけ実行する。
2026-10-26の検証では、手元の `.env` の上記API認証情報をプロセス内だけで
正規の環境変数名へ渡し、実APIのmeta・Revision 0/空Snapshot読取と10秒後の
RevisionPollerによるHTTP監視が成功した。Snapshot本文・認証情報は出力していない。
API補助試験の成功は実Botの切り替え完了を意味しない。
Botの実行ホスト・サービス名を特定できていないため、Bot停止・設定/DBバックアップ・
Bot設定変更・再起動は未実施。本番環境は変更していない。

同日の `gradlew test integrationTest build` は成功。
unit: 623件中622成功・1件skip（明示実行用golden生成）。
integration: 218件中205成功・13件skip（Docker利用不可のPostgreSQL 9件、
外部Discord送信の設定なし3件、通常実行ではopt-inしない上記smoke 1件）。
smokeは別実行で1件成功。Remote HTTP/変換/runtimeの122件とFactory追加5件は全件成功。

## SQLへのロールバックとToken更新

Botを停止し、バックアップしたconfigのdatabaseセクションと起動設定を戻す。
`type=sqlite` と元のDBパス、または `type=postgresql` と元の接続設定を復元して再起動する。
Remote期間中はローカルDBへ書いていないため、移行処理は不要。Local Interactionsは再び有効になる。
保存済みSQL設定はRemote期間のD1変更とは同期していないことに注意する。

Tokenの期限切れ・失効時は、固定メッセージの認証エラーを確認し、Accessの読取専用policyと
ステージング対象を点検する。新Tokenをsecret管理へ設定しBotを再起動する。
Tokenはプロセス起動時に取得するため、環境変数変更だけでは稼働中プロセスへ反映されない。
旧Tokenを失効させ、認証正常・revision監視再開を確認する。認証失敗で最後のSnapshotは破棄されない。

今後は別の認証・認可境界の管理用APIを実装してCleanup通知を接続し、
DiscordコマンドをWorkersのHTTP Interactionsへ移行する。D1 schema変更、本番のInteractions
Endpoint変更、本番Worker deploy、本番Botの設定変更はこのPRの対象外。

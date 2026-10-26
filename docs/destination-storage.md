# 配信設定のストレージ境界

Cloudflare D1を読むRemote Backendの設定・障害処理・ステージング切り替えは
[Remote Backend運用手順](remote-destination-storage.md)を参照。
`database.type=remote`ではSQLの初期化とLocal Interactionsを省略し、読取専用Cleanupを使う。
以下の抽象化時の比較と検証履歴はSQL構成を説明する。現在は同じSource境界にRemoteも接続する。

`DestinationAdminRegistry` は管理操作（管理用の読取を含む）、
`DestinationConfigSource` は配信用設定のバックグラウンド読取を担当する。
管理操作の既存APIとSQLトランザクション処理は維持している。

## 依存関係

変更前:

```text
EEWBotFactory → ChannelRegistrySql / DatabaseInitializer
             → DeliverySnapshotLoader / ConfigRevisionStore / SqlAdminRegistry
RevisionPoller → ConfigRevisionStore
SnapshotDeliveryRegistry → DeliverySnapshotLoader / ConfigRevisionStore
EEWBot → ChannelRegistrySql（終了）/ DestinationAdminRegistry（Gateway削除）
ChannelFilter → jOOQ Condition
```

変更後:

```text
EEWBotFactory → SqlDestinationBackend.open（構成の組立箇所のみ）
             → DestinationBackend.getConfigSource() → DestinationConfigSource
             → SqlDestinationBackend.createAdminRegistry(onWrite)
                 ├─ SlashCommandContext → SlashCommandHandler（Local Interactions）
                 └─ RegistryDestinationCleanup(adminRegistry)（セルフホストの後処理）

RevisionPoller ──────────┐
SnapshotDeliveryRegistry ┴→ DestinationConfigSource
                              ↑ SqlDestinationConfigSource
                                  ├─ ChannelRegistrySql
                                  └─ ConfigRevisionStore

EEWBot → DestinationBackend.close
       → DestinationCleanup（Gateway削除と終了時のsave）
EEWService → DestinationDeliveryRegistry / DestinationCleanup
SlashCommandContext → 管理APIと描画等の共有サービス（EEWServiceへの依存なし）
EEWService / TestMessageSlashCommand → WebhookSenderClient（既存senderへの送信）

配信 → DestinationDeliveryRegistry → immutable DeliverySnapshot（DBアクセスなし）
SQL検索 → ChannelFilterSql → ChannelFilterの条件
```

`DeliverySnapshotLoader` は `store.SqlDestinationConfigSource` に移動・改名した。
Snapshot管理とRevisionPollerはSQLバックエンドに含めない。
SQLの既存の2秒間隔、再読込要求の集約、AtomicReferenceによる交換を維持する。
Remoteは専用schedulerで既定10秒の固定遅延を使用する。
再読込失敗は即時再試行を繰り返さず、次回pollで再試行する。

## Revisionと更新の意味

設定更新とrevision更新は引き続きSqlAdminRegistryの同一トランザクション内で行う。
`onWrite` はcommitが成功し、対象行があった場合だけ実行する。
重複putはinsert-if-absent、更新0件・空のバッチはrevisionを増やさない。
複数行を更新する操作もrevisionは1回だけ増やす。

既存動作として、同じ値を再設定するsetterでも対象行にマッチすれば
revisionが増える。この点は既存の `sameValueUpdateDoesIncrement` テストに従い
変更していない。値の同一性による更新省略は今回の抽象化に含めない。

## SnapshotのRevision契約

Revisionは設定更新を検知するための単調増加する目印であり、返された配信先一覧と
厳密に同じ論理状態を指す必要はない。Sourceはcommit済みの配信設定を返し、
設定より古いRevisionを付けることができる。設定より先行したRevisionを返すと
必要な再読込を見逃すため、それは許容しない。
更新が止まった後は、成功する読込が最新のcommit済みRevisionへ追いつくことを要求する。
呼出側はSourceが返したRevisionを保持し、別途読んだRevisionへ付け直さない。
初期読込失敗は起動失敗、再読込失敗は旧Snapshotを保持する。

SQLiteのWAL読取トランザクションは、2つのSELECTの間に別接続がcommitしても
同じ状態を読む。PostgreSQLの通常のREAD COMMITTEDでは、Revision読取後に
commitが入ると、古いRevisionに新しい配信先が組み合わされる場合がある。
この組合せは上記契約で許容する。

古いRevisionが残っていれば、再読込後のRevision確認または次回pollで追加の再読込が
要求される。更新が止まればRevisionと設定は最新状態へ収束する。同じRevisionの
Snapshotが既にメモリにある場合は、その回の置換が省略されても後続の確認で追いつく。
分離レベルと既存のpolling・再読込方式は変更しない。

`PostgresqlDestinationStorageTest.readCommittedAllowsNewerSettingsWithAnEarlierRevision`
で、2つのSELECTの間に別接続でcommitし、Revision=1で新しい2配信先を取得する
許容ケースを検証する。SQLに依存しないRevisionPollerのテストでは、このように
Revisionが遅れたSnapshotから、追加の再読込で最新Revisionへ追いつくことを検証する。

## 起動と終了

SqlDestinationBackendが接続確立、既存Flyway migration、SQL管理レジストリと
Sourceの生成、DB解放を担当する。migration失敗でも取得済みのレジストリを閉じる。
Factoryは初期Snapshotを同期読込してからpollingを開始する。
Snapshot・poller初期化失敗でもbackendを閉じ、後続のDiscord接続やサービス生成の
失敗では、それまでに取得したリソースを解放する。closeは多重呼出可能。

通常終了の順序はGatewayManager → RevisionPoller → Discord logout →
イベント購読解除 → worker終了待機 → Snapshot executor終了待機 → DestinationCleanup.save →
backend.close → 外部Webhook終了。終了処理で通常の例外が起きても後続の解放を続ける。
Guildの一時的なunavailableイベントではDestinationCleanupを呼ばない。
チャンネルとスレッドの削除は従来どおりtarget ID単位で行い、親削除時の新しい
カスケード削除は追加していない。

## 検証と今後の追加作業

FakeDestinationConfigSourceを使ったユニットテストで、SQL実装なしの注入、
revision監視、再読込要求の集約、読込失敗、配信中のSource非呼出を検証する。
SQLite/PostgreSQL共通のストレージ契約テストでは、原子性、commit後の通知、
更新0件、バッチ、64bit ID、Webhook/Thread/NULL/各フラグ、削除、
SQL・Channel・DeliverySnapshotのフィルタ判定一致を検証する。
バックエンド再接続と、DBを閉じた後でもSnapshotから配信先を選べることも検証する。
schema、migration、設定の既定値には変更がない。

2026-10-23の実行結果（Windows / Zulu JDK 25、現行ビルド定義を使用）:

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
.\gradlew.bat test integrationTest build --console=plain
```

3タスクとも成功。ユニットテストは495件成功・1件スキップ（明示指定時だけ動かす
golden生成）、統合テストは205件成功・12件スキップ。統合のスキップは
Docker未起動のPostgreSQL 9件と、Discord接続設定がない外部送信テスト3件。
実行可能なSQLite統合テストは成功した。Interactionsの全購読解除・部分起動失敗、
管理機能なしの読取backend起動、cleanupの更新/save回数、共有sender transportの
query・ヘッダ・JSON互換性も検証した。/testmessageのコマンド単位テストでは、
未登録・Webhookなしの通常送信と成功応答、403・429・404・500・通信例外時の
エラー応答、管理APIやWebhook senderを呼ばないことを検証する。

将来Workers/D1へ移すときは、上記Revision契約を満たすRemote Source、
管理機能の構成切替、Gateway削除を設定所有者へ通知するDestinationCleanup実装、
HTTP認証・障害処理・revisionの単調性の契約が必要になる。
配信ロジックとRevisionPollerは同じインターフェースを利用できる。
この抽象化時点ではRemote実装を含まなかった。現在はHTTP API v1用のRemote Sourceを追加している。

Coreで観測したGateway削除・配信先の403/404・Webhookの404・senderのnotfoundsは
DestinationCleanupへ通知する。セルフホスト用RegistryDestinationCleanupは従来の管理レジストリを使い、
1対象ごとの更新とrevision増加、save回数、URL指定時の一括処理を維持する。
完全な読取専用Coreへ移す際にはこのadapterを設定所有者への通知実装に差し替える。
Sourceのrevisionは既存の比較方式に合わせて単調増加を前提とするため、DB復元や
Remoteでは巻戻りを検出・ラッチし、旧Snapshot保持と運用者の再起動要求を行う。

## Local Interactionsの取り外し境界

Factoryの「Local Interactions」ブロックが、SlashCommandContextの作成と
SlashCommandHandlerの起動をまとめた組立箇所になる。将来このブロックを外せば、
Coreはコマンド登録もApplicationCommand/SelectMenu/Buttonの購読も行わない。
現在はdatabase.typeでこのブロックの起動を切り替える。Workers側コマンド実装は別作業。

SlashCommandHandler自身がコマンド登録リクエストと3種のイベント購読を所有する。
FactoryはDisposableとして通常の購読一覧へ追加し、正常終了・後続の起動失敗で
解放する。Handler自身の初期化失敗でも開始済みの購読を解放し、二重disposeは安全。
購読停止後も既に登録されたDiscordのglobal command定義は保持される。

DestinationBackendには読取Sourceとcloseだけが必要であり、管理APIを要求しない。DestinationCleanupも管理APIを公開せず、
ローカルのRegistryDestinationCleanupだけが管理レジストリに依存する。
管理レジストリを作る能力はローカルのSqlDestinationBackendに置いている。
管理機能なしのbackendでも初期Snapshot読込とrevision監視を起動できる。
EEWBot・EEWService・GatewayManagerはDestinationAdminRegistryへ依存しない。
Coreの終了時に必要な従来のsaveはDestinationCleanupのadapterで引き継ぐ。

TestMessageSlashCommandは共有のWebhookSenderClientとDiscord RESTを使用し、
SlashCommandContextからEEWServiceへの依存を除いた。既存senderの送信先URL、
query、ヘッダ、JSON、単発送信のステータス処理は共通のtransportで維持している。
EEWBotの未使用の管理・描画・ユーザー情報保持フィールド、GatewayManagerの未使用の
Discordクライアント・管理レジストリ引数も削除した。

CoreにはDiscordへの通常配信、Gatewayの削除イベント、I18n、Renderer、
QuakeInfoStoreが引き続き必要であり、これらの共通機能は残している。
将来HTTP Interactionsへ移す際にはコマンド処理の移行に加えて、quakeinfo/testmessage等が
必要とするCoreの情報・機能への連携を設計する必要がある。

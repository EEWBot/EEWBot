# 配信設定のストレージ境界

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
             → DestinationBackend
                 ├─ createAdminRegistry(onWrite) → DestinationAdminRegistry
                 └─ getConfigSource() → DestinationConfigSource

RevisionPoller ──────────┐
SnapshotDeliveryRegistry ┴→ DestinationConfigSource
                              ↑ SqlDestinationConfigSource
                                  ├─ ChannelRegistrySql
                                  └─ ConfigRevisionStore

EEWBot → DestinationBackend.close
       → DestinationCleanup（Gateway削除）
           └─ using(adminRegistry): 従来のremove/removeByGuildId + save

配信 → DestinationDeliveryRegistry → immutable DeliverySnapshot（DBアクセスなし）
SQL検索 → ChannelFilterSql → ChannelFilterの条件
```

`DeliverySnapshotLoader` は `store.SqlDestinationConfigSource` に移動・改名した。
Snapshot管理とRevisionPollerはSQLバックエンドに含めない。
既存の2秒間隔、再読込要求の集約、AtomicReferenceによる交換を維持する。

## Revisionと更新の意味

設定更新とrevision更新は引き続きSqlAdminRegistryの同一トランザクション内で行う。
`onWrite` はcommitが成功し、対象行があった場合だけ実行する。
重複putはinsert-if-absent、更新0件・空のバッチはrevisionを増やさない。
複数行を更新する操作もrevisionは1回だけ増やす。

既存動作として、同じ値を再設定するsetterでも対象行にマッチすれば
revisionが増える。この点は既存の `sameValueUpdateDoesIncrement` テストに従い
変更していない。値の同一性による更新省略は今回の抽象化に含めない。

## Snapshotの整合性契約と既存PostgreSQLの制約

`DestinationConfigSource.loadSnapshot()` はrevisionと配信先が同じ論理状態を
表すSnapshotを返すことを契約とする。整合性の責任はSource側にあり、呼出側は
事前に取得したrevisionをSnapshotへ付け直してはならない。
初期読込失敗は起動失敗、再読込失敗は旧Snapshotを保持する。

SQLiteのWAL読取トランザクションは、2つのSELECTの間に別接続がcommitしても
同じ状態を読む。`SqliteDestinationStorageTest` でこの状況を検証する。

PostgreSQLには既存の契約違反が残る。接続の分離レベルを指定していないため、
通常のREAD COMMITTEDではrevision読取と配信先読取の間にcommitが入ると、
古いrevisionに新しい配信先が組み合わされる。
[PostgreSQL公式ドキュメント](https://www.postgresql.org/docs/current/transaction-iso.html)
でも、同一トランザクション内のSELECTごとに状態が変わり得ることを説明している。
この変更では既存の分離レベルを変更していない。

`PostgresqlDestinationStorageTest` に次の2ケースを追加した:

- `readCommittedReproducesExistingSnapshotConsistencyLimitation`: 2回のSELECT間に
  別接続で更新し、revision=1に2配信先が組み合わされる既存の問題を再現する。
- `transactionLocalRepeatableReadWouldKeepSnapshotConsistent`: 読取トランザクションの
  最初に `SET TRANSACTION ISOLATION LEVEL REPEATABLE READ` を行う修正案を検証する。
  revision=1・1配信先になり、その後の接続の分離レベルはREAD COMMITTEDに戻る。

修正案はSnapshot読取トランザクションだけにREPEATABLE READを指定する方式。
管理書込や接続プール全体の既定値は変更せず、同時更新・ロールバック・プール再利用を
実際のPostgreSQLで検証してから適用する。今回の実行環境ではDockerデーモンが
起動していないため、PostgreSQLテストはコンパイル確認のみで実行はスキップされた。

## 起動と終了

SqlDestinationBackendが接続確立、既存Flyway migration、SQL管理レジストリと
Sourceの生成、DB解放を担当する。migration失敗でも取得済みのレジストリを閉じる。
Factoryは初期Snapshotを同期読込してからpollingを開始する。
Snapshot・poller初期化失敗でもbackendを閉じ、後続のDiscord接続やサービス生成の
失敗では、それまでに取得したリソースを解放する。closeは多重呼出可能。

通常終了の順序はGatewayManager → RevisionPoller → Discord logout →
イベント購読解除 → worker終了待機 → Snapshot executor終了待機 → admin.save →
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

3タスクとも成功。ユニットテストは480件成功・1件スキップ（明示指定時だけ動かす
golden生成）、統合テストは205件成功・13件スキップ。統合のスキップは
Docker未起動のPostgreSQL 10件と、Discord接続設定がない外部送信テスト3件。
実行可能なSQLite統合テストは成功した。

将来Workers/D1へ移すときは、整合したrevision/Snapshotを取得するRemote Source、
管理機能の構成切替、Gateway削除を設定所有者へ通知するDestinationCleanup実装、
HTTP認証・障害処理・revisionの単調性の契約が必要になる。
配信ロジックとRevisionPollerは同じインターフェースを利用できる。
今回はWorkers/D1、HTTP API、Remote実装を追加していない。

残るCore側の書込として、EEWServiceは配信エラー時の配信先削除・Webhook解除を
DestinationAdminRegistry経由で行う。今回その挙動は変更していない。
完全な読取専用Coreへ移す際には、Gateway削除に加え、これらの後処理も設定所有者へ
委譲する必要がある。またSourceのrevisionは既存の比較方式に合わせて単調増加を
前提とするため、DB復元やRemote切替時のrevision巻戻りは別途設計が必要になる。

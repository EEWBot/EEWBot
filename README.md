# EEWBot
Earthquake Early Warning(EEW) Discord Bot for Japan

緊急地震速報(以下EEW)を、強震モニタから取得し、Discordに通知するBotです。  
標準設定では、高度利用者向け緊急地震速報(予報)も通知されます。

## お知らせ
2.0.3以前のバージョンは、緊急地震速報のエンドポイントが変更され利用出来なくなったため、緊急地震速報が提供されません。
速やかなバージョンアップをお願い致します。ご迷惑をおかけして申し訳ございません。

## リンク
#### 公式インスタンス
自宅サーバーで稼働しているため、可用性が保証されません。
[Bot招待リンク](https://discordapp.com/oauth2/authorize?client_id=329257498668302346&scope=bot)
#### 公式サポートサーバー
[招待リンク](https://discord.gg/wATGHHY)

## セットアップ
### Token生成
すでに存在している場合は必要ありません。
1. [Discord Developers](https://discordapp.com/developers/applications/me) にアクセス
1. New Application から任意の名前をつけてApplicationを作成
1. Settings > Bot > Add Bot
1. TokenをCopy

## Windows
1. Java8がインストールされていない場合はインストールする
1. [Releases](https://github.com/Team-Fruit/EEWBot/releases/latest) より、jarをダウンロード
1. 設定ファイルなどが生成されるため、フォルダを作成し、その中にダウンロードしたjarを配置
1. コマンドプロンプトまたはPowerShellを開き、以下のコマンドをファイル名を正しいものに変更し、実行する
```
java -Dfile.encoding=UTF-8 -jar eewbot.jar
```
5. 生成されたconfig.jsonにのtokenにDiscord BotのTokenを入力
5. 生成されたpermission.jsonのownerに自身のDiscordアカウントのIDを入力 (Discordクライアントの開発者モード時にIDをコピーで得られるもの)
5. 先程のコマンドを再実行し、Botが起動することを確認する

## Docker
[Docker Hub](https://hub.docker.com/r/teamfruit/eewbot)よりimageをpull
```sh
$ docker pull teamfruit/eewbot
```
設定ファイル置き場のホストのディレクトリを作成、移動
```sh
$ mkdir eewbot && cd $_
```
新規インスタンス作成
```sh
$ docker volume create --name eewbot
$ docker run -e TOKEN=<Your bot token> \
             -v ${PWD}:/etc/eewbot \
             -v eewbot:/var/lib/eewbot \
             --name eewbot \
             -t -d teamfruit/eewbot
```
別の環境からの移行の場合
```sh	
$ docker run -e TOKEN=<Your bot token> \	
             -v ${PWD}:/etc/eewbot \	
             -v ${PWD}:/var/lib/eewbot \	
             --name eewbot \	
             -t -d teamfruit/eewbot	
```

## コマンド
Prefix `!eew`

|コマンド|説明|
|---|---|
|register|通知するチャンネルを登録し、セットアップします|
|unregister|チャンネルの登録を解除します|
|add|チャンネルに通知される情報を追加します|
|remove|チャンネルに通知される情報を消去します|
|detail|登録されたチャンネルの設定を表示します|
|quakeinfo|最新の地震情報を取得します|
|monitor|現在の強震モニタの画像を取得します|
|reload|設定ファイルをリロードします|
|joinserver|Botの招待リンクを表示します|
|time|Botの時刻同期情報を表示します|
|timefix|Botの時刻を強制的に修正します|
|setlang|サーバー標準言語や登録チャンネルの言語設定をします|
|help|helpを表示します|

## ライセンス
- EEWBot
  - [![License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat)](https://github.com/Team-Fruit/EEWBot/blob/master/LICENSE.md)
- [Discord4J](https://github.com/austinv11/Discord4J)
  - [![License](https://img.shields.io/badge/License-LGPLv3-blue.svg?style=flat)](https://github.com/austinv11/Discord4J/blob/master/LICENSE.txt)

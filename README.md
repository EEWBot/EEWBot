# EEWBot
Earthquake Early Warning(EEW) Discord Bot for Japan

緊急地震速報(以下EEW)を、強震モニタから解析し、Discordに通知するBotです。  
設定によっては、高度利用者向け緊急地震速報(予報)も通知する事が可能です。

## セットアップ (jar)
1. DiscordのBotアプリケーションを持っていない場合は、[Discord Developers](https://discordapp.com/developers/applications/me)よりBotを作成し、BotユーザーのTokenを生成してください。  
1. jarを[Releases](https://github.com/Team-Fruit/EEWBot/releases/latest)よりダウンロード後、フォルダを作って配置し、起動してください。  
コマンドラインから起動する事をおすすめします。
1. ファイルが生成されてBotが終了するので、`config.json`をテキストエディタで開き、空の文字列になっているtokenにBotのTokenを入れてください。
1. 再びBotを起動し、Botが入っているDiscordサーバーで、オンラインになっている事を確認してください。

## セットアップ (Docker)
[Docker Hub](https://hub.docker.com/r/teamfruit/eewbot)よりimageをpull
```sh
$ docker pull teamfruit/eewbot
```
設定ファイル置き場のホストのディレクトリを作成、移動
```sh
$ mkdir eewbot && cd $_
```
`channels.json`がない場合 (新規インスタンス作成)
```sh
$ docker volume create --name eewbot
$ docker run -e TOKEN=<Your bot token> \
             -v ${PWD}:/etc/eewbot \
             -v eewbot:/var/lib/eewbot \
             --name eewbot \
             -t -d teamfruit/eewbot
```
`channels.json`がすでにある場合 (別の環境からの移行)
```sh
$ docker run -e TOKEN=<Your bot token> \
             -v ${PWD}:/etc/eewbot \
             -v ${PWD}:/var/lib/eewbot \
             --name eewbot \
             -t -d teamfruit/eewbot
```

## コマンド
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

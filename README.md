# EEWBot
Earthquake Early Warning(EEW) Discord Bot for Japan

緊急地震速報(以下EEW)を、強震モニタから解析し、Discordに通知するBotです。  
設定によっては、高度利用者向け緊急地震速報(予報)も通知する事が可能です。

## セットアップ (jar または exe)
1. DiscordのBotアプリケーションを持っていない場合は、[Discord Developers](https://discordapp.com/developers/applications/me)よりBotを作成し、BotユーザーのTokenを生成してください。  
1. jarまたはexeを[Releases](https://github.com/Team-Fruit/EEWBot/releases/latest)よりダウンロード後、フォルダを作って配置し、起動してください。  
jarの場合は、コマンドラインから起動する事をおすすめします。
1. ファイルが生成されてBotが終了するので、`config.json`をテキストエディタで開き、空の文字列になっているtokenにBotのTokenを入れてください。
1. 再びBotを起動し、Botが入っているDiscordサーバーで、オンラインになっている事を確認してください。

## セットアップ (Docker)
#### クイックスタート
```sh
$ docker pull teamfruit/eewbot
```
```sh
$ mkdir eewbot && cd $_
$ docker run -e TOKEN=<Your bot token> \
             -v ${PWD}:/etc/eewbot \
             -v ${PWD}:/var/lib/eewbot \
             --name eewbot \
             -t -d teamfruit/eewbot
```

## コマンド
|コマンド|説明|
|---|---|
|help|helpを表示します|
|register|チャンネルをEEWBotに登録します|
|unregister|チャンネルをEEWBotから登録解除します|
|add|登録したチャンネルに通知する情報の種類を追加します<br>利用可能な種類は`help add`コマンドで参照出来ます|
|remove|登録したチャンネルに通知する情報の種類を消去します<br>利用可能な種類は`help add`コマンドで参照出来ます|
|details|登録したチャンネルの設定を確認します|
|quakeinfo|最新の地震情報を表示します<br>Yahoo!の地震情報のURLを指定すると、過去の地震情報を表示出来ます|
|monitor|強震モニタの画像を取得します|
|reload|設定ファイルをリロードします|
|joinserver|Botの招待リンクを表示します|
|test|緊急地震速報をテスト出来ます<br>データURLを指定するか、直接JSONを書き込めます|
|timefix|NTPサーバーに問い合わせて、時刻を修正します|

## ライセンス
- EEWBot
  - [![License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat)](https://github.com/Team-Fruit/EEWBot/blob/master/LICENSE.md)
- [Discord4J](https://github.com/austinv11/Discord4J)
  - [![License](https://img.shields.io/badge/License-LGPLv3-blue.svg?style=flat)](https://github.com/austinv11/Discord4J/blob/master/LICENSE.txt)

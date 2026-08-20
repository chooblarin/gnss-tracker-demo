# QZ1 GNSS Sample

QZ1 receiverからBluetooth Classic SPPでNMEA文を受信し、Android端末の
Phone GNSSと記録・比較するための学習用Androidアプリです。

このリポジトリでは、次の題材を扱います。

- Bluetooth Classic SPPによるストリーム受信
- Foreground Serviceによるバックグラウンド記録
- NMEA GGA/RMC文のパース
- Android Location/GNSS APIによる端末側測位
- ファイル保存、一覧、削除、共有
- 軌跡の地図表示と時刻ベースの位置差比較
- ViewModel、StateFlow、Repositoryを使った状態管理

> [!IMPORTANT]
> これは学習・検証用サンプルです。測量、航法、人命や安全に関わる用途には
> 使用しないでください。QZ1および「みちびき」に関係するメーカー・団体の
> 公式アプリではありません。

## 必要なもの

- Android Studio（Android Gradle Plugin 9.3.1をサポートする版）
- Android SDK 37
- Android 15（API 35）以上の実機
- Bluetooth Classic SPPに対応するQZ1 receiver
- Google Mapsを表示する場合のみ、Maps SDK for AndroidのAPIキー

BluetoothとGNSSは実機機能を使うため、エミュレーターだけでは一連の記録を
検証できません。

## セットアップ

1. リポジトリをクローンし、Android Studioで開きます。
2. Android Studioが要求するSDKとGradle JDKをインストールします。
3. QZ1 receiverをAndroidのBluetooth設定からペアリングします。
4. 実機を接続してappを実行します。
5. アプリでNearby devices権限を許可し、ペアリング済みQZ1を選択します。
6. StartでNMEA記録を開始します。

Phone GNSS比較は任意です。画面の「Enable phone comparison」を押してPrecise
Locationを許可すると、次回の記録から端末側の測位も同時保存されます。
位置情報を許可しなくてもQZ1のNMEA記録は継続できます。

## Google Maps APIキー

APIキーがなくてもNMEA受信、保存、パース、一覧表示、比較データの生成は
学習できます。地図を表示する場合だけ設定してください。

1. Google Cloud ConsoleでMaps SDK for Androidを有効にします。
2. APIキーを作成します。
3. local.properties.exampleを参考に、プロジェクト直下の
   local.propertiesへ次を追記します。

~~~properties
MAPS_API_KEY=YOUR_API_KEY
~~~

local.propertiesはGit管理外です。APIキーは次の制限を設定してください。

- Application restriction: Android apps
- Package name: com.example.qz1sample
- SHA-1: 使用するdebug/release署名証明書
- API restriction: Maps SDK for Android

debug証明書のSHA-1は次のコマンドで確認できます。

~~~bash
./gradlew :app:signingReport
~~~

APIキーをソースコードへ直接記述しないでください。

## 使い方

### QZ1を記録する

1. Refreshでペアリング済み端末を更新します。
2. QZ1を選び、Startを押します。
3. アプリをバックグラウンドにしても、Foreground Serviceが記録を続けます。
4. Stopで記録を終了します。

### 保存データを見る

Logsから次の操作ができます。

- NMEAログの一覧表示
- パース済み軌跡と測位情報の表示
- Phone GNSSを記録したセッションの位置差比較
- NMEAファイルの共有
- セッションの削除

保存データはアプリ内部のqz1_logs/に置かれます。位置履歴を含むため、
クラウドバックアップと端末間転送の対象から除外しています。

## 測位比較の意味

このアプリの比較値は、時刻が近いQZ1点とPhone GNSS点の水平距離です。
測量済み基準点に対する誤差ではなく、二つの受信結果の差を表します。

AndroidのPhone GNSSはGPSだけに限定されるとは限りません。端末、OS、
受信環境によって、複数の衛星測位システムや端末側の補正が関与します。
そのため「GPS対QZSSの厳密な性能試験」ではなく、同時観測の教材として
扱ってください。

## アーキテクチャ

詳しいデータフローと、読む順番は
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)を参照してください。

## ビルドと検証

~~~bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
~~~

Google Maps APIキーは通常のビルドや単体テストには不要です。

## 既知の制約

- QZ1の機種・ファームウェア差によってSPP接続やNMEA出力が異なる可能性があります。
- Phone GNSSの更新頻度や精度はAndroid端末ごとに異なります。
- NMEAパーサーは、このサンプルで必要なGGA/RMCを中心に実装しています。
- 現在のminSdkは35です。教材を古い端末へ広げる場合は、API互換性を
  確認しながら引き下げてください。
- Google Maps Platformの利用には、Google Cloud側の設定や料金条件が適用されます。

## ライセンス

このプロジェクトの自作コードは
[Apache License 2.0](LICENSE)で提供します。外部ライブラリ、Google Maps、
製品名、商標にはそれぞれの提供元の条件が適用されます。

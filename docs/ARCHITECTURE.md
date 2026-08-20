# Architecture

この文書は、QZ1 GNSS Sampleを教材として読むための案内です。

## データフロー

~~~mermaid
flowchart LR
    UI["Compose UI"] --> VM["ViewModels"]
    VM --> SERVICE["Qz1RecordingService"]
    SERVICE --> SPP["SppSession / Bluetooth SPP"]
    SPP --> BUFFER["NmeaLineBuffer"]
    BUFFER --> WRITER["NmeaLogWriter"]
    BUFFER --> PARSER["NmeaParser"]
    PARSER --> LIVE["Live GNSS status"]
    WRITER --> FILES["Recording session files"]
    FILES --> REPOSITORY["Session / Track repositories"]
    REPOSITORY --> BUILDER["TrackBuilder"]
    BUILDER --> MAP["Track map and summary"]
    SERVICE --> PHONE["PhoneGnssRecorder"]
    PHONE --> SIDECAR["Phone CSV sidecar"]
    SIDECAR --> COMPARE["TrackComparison"]
    BUILDER --> COMPARE
~~~

## 主な境界

| 境界 | 主な型 | 責務 |
| --- | --- | --- |
| UI | Qz1Screen, TrackDetailScreen | 状態の表示とユーザー操作 |
| UI状態 | Qz1MonitorViewModel, TrackViewModel | UI状態と操作の調停 |
| バックグラウンド記録 | Qz1RecordingService | セッション開始・停止と通知 |
| 接続 | SppSession | RFCOMM socketの接続、読み取り、切断 |
| ストリーム処理 | NmeaLineBuffer, NmeaParser | チャンクから行への復元とNMEA解釈 |
| 永続化 | NmeaLogWriter, PhoneLocationLogWriter | NMEAとPhone GNSSの追記 |
| セッション | RecordingSessionRepository | 主ファイルとsidecarの一体管理 |
| 軌跡 | TrackBuilder, TrackMetrics | 点列、距離、時間、要約の生成 |
| 比較 | TrackComparisonBuilder | 時刻の近いQZ1点とPhone点の一対一比較 |

## 記録セッション

一回の記録は、同じbasenameを持つ複数ファイルで構成されます。

- .nmea: QZ1から受信したNMEA文
- .phone.csv: Phone GNSSを許可した場合のsidecar
- .meta.json: 一覧表示を高速化する要約キャッシュ

.nmeaがprimaryです。Phone GNSSはoptional sidecarなので、位置情報権限が
ない場合やPhone側だけが失敗した場合でも、QZ1の記録を止めません。

## 並行処理で守ること

- Bluetooth I/Oとファイル書き込みはmain threadで行わない
- Serviceの開始・停止・再開始はlifecycle mutexの内側で直列化する
- 古い記録jobが新しいセッションを停止しない
- Phone GNSSの失敗をQZ1 recordingの失敗へ昇格させない
- UIはServiceのsocketやwriterを直接所有しない

## NMEA処理

NmeaLineBufferはRFCOMMから届く任意サイズのbyte chunkを、改行単位の
NMEA sentenceへ戻します。NmeaParserはchecksumを検証し、このサンプルで
必要なGGAとRMCをNmeaEventへ変換します。

TrackBuilderは次を行います。

1. 不正な文、未対応文、無効fixを除外
2. 同時刻のGGAとRMCを一つのTrackPointへ統合
3. 点間距離と累積距離を計算
4. duration、altitude、speedなどのsummaryを生成

## 比較処理

QZ1とPhone GNSSではsampling intervalが一致しないため、配列indexでは
比較しません。TrackComparisonBuilderはepoch timeを使い、時間差が許容範囲内の
相互に近い点を一対一で対応させます。一つのPhone点を複数のQZ1点へ
再利用しないことが重要な不変条件です。

算出値は二つの測位点の水平距離であり、ground truthに対する絶対誤差では
ありません。

## おすすめの読む順番

1. NmeaLineBufferTest
2. NmeaParserTestとNmeaParser
3. TrackBuilderTestとTrackBuilder
4. TrackComparisonTestとTrackComparisonBuilder
5. SppSession
6. RecordingSessionResources
7. Qz1RecordingService
8. Qz1MonitorViewModelとQz1Screen
9. TrackViewModelとTrackDetailScreen

まず純粋関数と単体テストを読み、その後Android frameworkに依存する境界へ
進むと追いやすくなります。

## 学習課題の例

- GSAやGSV sentenceのparserを追加する
- checksum不正数をUIへ表示する
- synthetic sampleからsummaryを生成するテストを追加する
- comparisonの時間窓を変更し、paired point数への影響を調べる
- minSdkを引き下げ、必要なversion branchを整理する
- Google Maps以外のmap rendererをrepository境界の外側で差し替える

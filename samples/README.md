# Synthetic samples

このディレクトリのファイルは、実在人物の行動履歴を使わずに生成した
学習・テスト用データです。

## synthetic_track.nmea

- 2026-01-01 12:00:00 UTCから10秒間
- GGAとRMCを1 Hzで収録した形式
- 緯度35度、経度135度付近から一定方向へ移動する合成軌跡
- NMEA checksum付き

## synthetic_track.phone.csv

synthetic_track.nmeaと同時刻のPhone GNSS sidecarです。QZ1側の合成点から
小さくoffsetした座標を持つため、TrackComparisonの入力例として使えます。

このCSVはアプリが保存するsidecar schemaと同じです。二つのファイルは
basenameを共有し、RecordingSessionFilesの規則を示しています。

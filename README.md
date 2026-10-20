# compsend

Kotlin + Jetpack Compose の Android アプリ(パッケージ名: `com.toshi0907.compsend`、minSdk 33)。

## ビルドと配布

- すべての push / PR で GitHub Actions がデバッグ APK をビルドし、Artifact として保存します。
- main への push 時は、固定タグ `latest` の Release が最新のビルドで置き換えられます。
  - 最新 APK: https://github.com/toshi0907/compsend/releases/download/latest/compsend.apk
- 署名にはリポジトリにコミットしたデバッグ専用の鍵(`app/debug.keystore`)を使うため、上書きインストールできます。

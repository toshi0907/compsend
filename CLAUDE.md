# compsend

## 利用可能なスキル(myskills 由来)

このリポジトリの `.claude/skills/` は実ディレクトリで、[myskills](https://github.com/toshi0907/myskills)
を submodule として取り込んだ `.myskills/claude-skills/` 配下の各スキルへの symlink が
スキルごとに登録されています(`.claude/skills` 自体はsymlinkではありません)。
リポジトリ固有のローカルスキルがある場合は、同じ `.claude/skills/` 内に通常のディレクトリとして
共存できます。
Claude Code on the web のセッション開始時に、`.claude/hooks/myskills-skills-sync.sh` が
`.myskills` を最新化し、未登録のスキルsymlinkの追加とリンク切れsymlinkの削除を行います
(既存のsymlinkやローカルスキルは上書きしません)。

実装作業を始める前に、`.claude/skills/` にあるスキルの一覧と各 `SKILL.md` の
description を確認し、該当するものがあれば優先的に使ってください。

フックが「myskills同期による未コミットの差分」を報告した場合(`.myskills` の更新、
`.claude/skills/` 配下のsymlinkの追加・削除)は、作業内容のコミットに混ぜず、
該当パスだけを専用のコミット(例: `chore: sync myskills`)として作業ブランチにコミットしてください
(`git add -A` / `git commit -a` で作業コミットに巻き込まないこと)。

## Android開発の前提

- Kotlin + Compose、パッケージ名 `com.toshi0907.compsend`、minSdk 33。
- 開発環境には Android SDK がなく、`dl.google.com` にも接続できないため、ローカルの `./gradlew` は最後まで実行できない。
  検証はコードを読んで行い、実際のビルドは GitHub Actions(`.github/workflows/android.yml`)の結果で確認する。
- `app/debug.keystore` は署名を固定するためのデバッグ専用の鍵で、意図してコミットしている。
- ビルド番号には `GITHUB_RUN_NUMBER` を使う(`BuildConfig.BUILD_NUMBER`)。main への push 時に、Release `latest` の本文 `build_number: N` も更新する。

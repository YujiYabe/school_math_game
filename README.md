# 算数アプリ

小学生が四則演算を繰り返し練習できる Android 向け算数ドリルです。
問題は4つの答えから選ぶ形式で、計算の反復練習から間違い直しまでを
1台の端末で行えます。

## 主な機能

- 足し算・引き算・掛け算・割り算の4択問題
- 1問あたりの制限時間を1〜30秒で設定
- 1回の問題数を5〜100問で設定
- 間違えた問題だけを解き直す復習機能
- 得点、回答内容、実施日時を確認できる学習履歴
- 出題する演算を保護者が選べる管理画面
- 正解数に応じて利用時間を付与するアプリ内 YouTube 機能

足し算と引き算は2桁まで、掛け算は九九、割り算は九九の範囲で
割り切れる問題を出題します。

## アプリの使い方

1. メニューで練習する演算を選びます。
2. 1問あたりの制限時間と問題数を設定します。
3. 「スタート」を押し、表示された4つの候補から答えを選びます。
4. 終了後に結果を確認します。間違いがある場合は、その問題だけを解き直せます。
5. 過去の結果はメニューの「履歴」から確認できます。

### 保護者向け設定

「管理画面」では、出題する演算、YouTube の付与時間、Wi-Fi、
管理パスワードを設定できます。初回の管理パスワードは空欄です。
何も入力せずに認証し、最初に4桁以上のパスワードへ変更してください。

YouTube 機能を利用しない場合は、管理画面で「100問正解につき可能な視聴時間」と
現在の残り時間を0分に設定してください。

## 動作環境

- Android 6.0（API 23）以上
- インターネット接続（アプリ内 YouTube 機能を利用する場合）

## 開発環境のセットアップ

### 必要なもの

- Git
- JDK 17以上（Android Studio 同梱の JBR も利用できます）
- Android Studio または Android SDK
- Android SDK Platform 36.1

このプロジェクトは、同じ親ディレクトリにある
[`android_admin_common`](https://github.com/YujiYabe/android_admin_common) を
composite build として参照します。次の配置になるように両リポジトリを clone してください。

```text
任意の作業ディレクトリ/
├── android_admin_common/
└── school_math_game/
```

```sh
git clone https://github.com/YujiYabe/android_admin_common.git
git clone https://github.com/YujiYabe/school_math_game.git
cd school_math_game
```

Android Studio で `school_math_game` を開き、Gradle Sync を実行してください。
通常は、使用する Android SDK に合わせて `local.properties` が自動生成されます。

コマンドラインだけでセットアップする場合は、自分の環境の SDK パスを
`ANDROID_HOME` に設定し、`local.properties` を作成します。

```sh
export ANDROID_HOME="<Android SDK のインストール先>"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
```

`local.properties` は開発環境ごとのファイルであり、Git の管理対象には含まれません。
JDK の場所は `JAVA_HOME` または Android Studio の Gradle JDK 設定で指定してください。

## ビルドとインストール

macOS / Linux:

```sh
./gradlew :app:assembleDebug
```

Windows:

```bat
gradlew.bat :app:assembleDebug
```

デバッグ APK は次の場所に生成されます。

```text
app/build/outputs/apk/debug/app-debug.apk
```

USB デバッグを有効にした端末または起動中のエミュレーターへインストールするには、
次のコマンドを実行します。

```sh
./gradlew :app:installDebug
```

## 技術構成

- Kotlin
- Jetpack Compose / Material 3
- Android Gradle Plugin
- Gradle Wrapper

## データについて

設定と学習履歴は端末内に保存されます。アプリをアンインストールすると、
端末の設定によってバックアップから復元される場合を除き、これらのデータは削除されます。

## ライセンス

このプロジェクトは [MIT License](LICENSE) のもとで公開されています。

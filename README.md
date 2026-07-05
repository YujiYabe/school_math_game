# school_math_game

Android / Kotlin / Jetpack Compose の100本ノック型算数ドリルです。

## Build

この環境では `/home/yuji/Android/Sdk` を使う `local.properties` を作成済みです。

```sh
./gradlew :app:assembleDebug
```

ビルドには JDK 17 以上が必要です。この環境では Android Studio 同梱の JBR を `gradle.properties` の `org.gradle.java.home` に設定済みです。

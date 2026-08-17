# MarketPanel

MarketPanelは、古いAndroidタブレットを株価・ETF・市場指標の常時表示モニターとして再利用するためのアプリです。

米国株・ETF、国内投信の参考指標、市場指数・為替を3つのパネルに表示し、設定した間隔で自動的にローテーションします。実データはCloudflare Workerを経由してTwelve Dataから取得します。

## 主な機能

- 横画面専用の常時表示ダッシュボード
- 3つの表示パネル
  - `MAIN`: 米国株・米国ETF
  - `SUB1`: 国内投信の参考指標
  - `SUB2`: 市場指数・為替
- 銘柄の追加、編集、削除、並べ替え、有効・無効設定
- 5秒（デバッグ用）から5分までの表示ローテーション
- 通常時はMAIN、SUB1、SUB2の切り替えを10秒ずつずらして表示
- 現在値と週足チャートの端末内Roomキャッシュ
- Cloudflare CacheとKVによるサーバー側キャッシュ
- Twelve DataのAPI使用量表示
- ステータスバーを使わないバッテリー残量・充電状態表示
- MAIN／SUB1／SUB2別の銘柄管理とドラッグによる表示順変更
- Android標準ファイルピッカーを使った設定のJSONバックアップ／リストア
- API呼び出し・キャッシュ・推定クレジットの端末ログ
- APIエラー時の再試行、連続失敗時の一時停止
- 全画面表示、画面点灯維持、端末起動時の自動起動

## システム構成

```text
Androidタブレット
  ├─ MarketPanel Androidアプリ
  ├─ Room（株価・チャートキャッシュ）
  └─ DataStore（表示・銘柄設定）
          │ HTTPS
          ▼
Cloudflare Worker
  ├─ APIキーの秘匿
  ├─ レスポンス形式の変換
  ├─ Cloudflare Cache
  └─ Workers KV（共有キャッシュ）
          │
          ▼
Twelve Data API
```

## ビルドバリアント

| Variant | データ | Application ID | 用途 |
|---|---|---|---|
| `debug` | デモデータ | `com.digihori.marketpanel.debug` | UI・ローテーション確認 |
| `liveDebug` | 実データ | `com.digihori.marketpanel.live` | 実データの動作確認 |
| `release` | 実データ | `com.digihori.marketpanel` | 配布用 |

Android Studioでは、`Build > Select Build Variant`から切り替えます。

## 必要なもの

- Android Studio
- JDK 17（Android Studio同梱JBRを利用可能）
- Android SDK 35
- Node.js
- Cloudflareアカウント
- Twelve Data APIキー

## Androidアプリのセットアップ

1. Android Studioでプロジェクトを開きます。
2. `local.properties`にAndroid SDKの場所を設定します。

   ```properties
   sdk.dir=/Users/your-name/Library/Android/sdk
   ```

3. `debug`または`liveDebug`を選択して実行します。

コマンドラインからビルドする場合:

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleLiveDebug
```

生成されるAPK:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/liveDebug/app-liveDebug.apk
```

## Cloudflare Workerのセットアップ

```sh
cd worker
```

ローカル開発用の環境変数ファイルを作成します。

```sh
cp .dev.vars.example .dev.vars
```

`.dev.vars`へTwelve Data APIキーを設定します。

```properties
TWELVE_DATA_API_KEY=your_api_key
```

Cloudflareへ本番用シークレットを登録します。

```sh
npx wrangler secret put TWELVE_DATA_API_KEY
```

KV namespaceのbindingは`MARKET_CACHE`です。環境に合わせて`worker/wrangler.toml`のnamespace IDを設定してください。

Workerをデプロイします。

```sh
npx wrangler deploy
```

Worker単体テスト:

```sh
npm test
```

## キャッシュとAPIクレジット

Android端末側:

- 現在値: 2時間
- 週足チャート: 24時間
- 期限切れ後に通信できない場合は保存済みデータへフォールバック

Cloudflare Worker側:

- 現在値・市場指標: 2時間
- チャート: 24時間
- API使用量: 15分

APIログは設定画面の「APIログ」から確認できます。

- `HIT`: Cloudflare Cacheから応答（推定0クレジット）
- `KV`: Workers KVから応答（推定0クレジット）
- `MISS`: Twelve Dataへアクセス
- `POLICY-BLOCK`: 失敗抑制中のためアクセスなし（0クレジット）

## エラー時の再試行

- `429`: 60秒後に1回だけ再試行
- 通信エラー・`5xx`: 15秒後に1回だけ再試行
- `400`・`404`: 再試行せず、翌日0時まで停止
- 最終的な失敗が3回連続: 6時間停止
- 成功時: 連続失敗回数をリセット

失敗状態と停止期限は端末へ保存され、アプリを再起動しても維持されます。

## テスト

Android単体テストとAPKビルド:

```sh
./gradlew testDebugUnitTest :app:assembleDebug :app:assembleLiveDebug
```

Workerテスト:

```sh
npm test --prefix worker
```

## 秘密情報

以下のファイルはGitへコミットしないでください。

- `local.properties`
- `worker/.dev.vars`
- `worker/.wrangler/`

Twelve Data APIキーをAndroidアプリや`wrangler.toml`へ直接書かないでください。本番用のキーはWranglerのsecretとして登録します。

## パッケージ

- アプリ名: MarketPanel
- パッケージ名: `com.digihori.marketpanel`

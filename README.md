# app-classifieds

`cloud-itonami/app-classifieds` は、地域別の **売買・求人・住居・サービス**を
ひとつの画面で横断する、Craigslist 型の分類掲示板です。

この repo は既存ドメインを置き換えません。公開カタログの `fleamarket`、求人の
`shigotoba`、ギグ/サービスの `hc`、物件の `real-estate` を、共通の
`classifieds/listing` 表示契約へ投影する **public app / aggregation surface** です。

## 現在動く縦切り

- 地域、4カテゴリ、全文語による即時絞り込み
- 4つの既存repoを出所として明示した統一カード
- 「掲載する」から作った投稿をブラウザの `localStorage` に保存・再表示
- `Need`（探している）と `Seed`（提供できる）の出所つき正規化
- `ossekkai` による理由・点数つき候補提案（提案時点では連絡しない）
- 双方同意後だけ `nakoudo` が紹介スレッドを開く状態機械
- `kotoba-lang/org-signal` の X3DH + Double Ratchet によるブラウザ内暗号化往復
- 住所を受け取らない配送 capability token（開示先は配送業者だけ）
- provider capture・配送完了・紛争なし・名前付き人間承認を要求するエスクロー判断
- stdio / Streamable HTTP MCP（検索・取込正規化・マッチ提案）と A2A v1 Agent Card / endpoint
- 参加者DID、SHA-256 content addressing、PII非公開を宣言するWeb3 binding
- JavaScript無効時にもサンプル一覧を読めるSSR-first HTML
- 1 document / 1 bundle / 1 mount の単一ページ
- `jp-go-digital-design-system` と `--hig-*` token contract によるUI

`localStorage` 投稿と取引フローは **この端末だけの実行可能デモ**です。MCP と A2A の
read/proposal surface は Cloudflare Workers へ公開済みで、既存 fulfillment / settlement
actor の `/health` を Service Binding 経由で確認します。本人確認、通報、モデレーション、
Signal Messengerアカウントへの配送、PSP送金、配送業者予約、応募・契約は未接続です。

## 信頼フロー

```text
Need + Seed
  -> ossekkai match proposal（理由つき・未連絡）
  -> Need側同意 + Seed側同意
  -> nakoudo introduction
  -> Signal X3DH + Double Ratchet thread
  -> carrier-only shipment capability / escrow proposal
  -> provider capture + delivery evidence + no dispute + named human approval
  -> external rail release（このrepoの外、ここでは実行しない）
```

公開面へ住所、メッセージ平文、秘密鍵、決済credentialを置きません。匿名配送は
「相手方と掲示板から住所を秘匿する」境界であり、配送業者・税関・法令上必要な主体から
秘匿するという意味ではありません。

## Agent / Bot / MCP

```bash
npm run mcp:smoke
node scripts/mcp-server.mjs
```

実装済みtool:

- `classifieds.search`
- `classifieds.intake.normalize`
- `classifieds.match.propose`

Botは出所・観測時刻を保持したNeed/Seedを取り込み、候補を提案できます。外部への連絡、
契約、支払い、エスクロー解除、紛争裁定はtoolに含めていません。

公開 endpoint:

- `https://cloud-itonami-app-classifieds-production.04-feasts-minded.workers.dev/`
- `POST /mcp` — JSON-RPC MCP
- `POST /a2a` — A2A v1 JSON-RPC `SendMessage`
- `POST /message:send` — A2A v1 HTTP+JSON
- `GET /.well-known/agent-card.json` — A2A Agent Card
- `GET /api/connectors/status` — actor と外部効果の現在地
- `POST /api/shipment/capability` — 住所を受け取らない配送 capability
- `POST /api/escrow/plan` — 資金を動かさないエスクロー解除計画

`/api/escrow/release`、`/api/payment/capture`、`/api/payment/transfer` は常に 403、
`/api/signal/send` はアカウント-backed bridge 未設定の間 503 で fail closed します。

生成されるdiscovery artifacts:

- `public/.well-known/agent-card.json`
- `public/protocol/mcp-tools.json`
- `public/protocol/web3-binding.json`

## 境界

| カテゴリ | source repo | このrepoの責任 |
|---|---|---|
| 売買 | `cloud-itonami/fleamarket` | 出品を共通カードへ投影 |
| 求人 | `cloud-itonami/shigotoba` | 求人を共通カードへ投影 |
| 住居 | `cloud-itonami/real-estate` | 物件を共通カードへ投影 |
| サービス | `cloud-itonami/hc` | ギグ/サービスを共通カードへ投影 |

原本、取引、応募、決済、契約は各 source repo の責任です。ここは検索・発見・投稿入口を
束ねるだけで、各領域の記録を勝手に所有しません。

## 検証

```bash
kbb -M:test
kbb -M:render public/index.html
kbb -M:build
npm run mcp:smoke
```

生成した `public/index.html` を静的HTTPサーバで開くと動作を確認できます。

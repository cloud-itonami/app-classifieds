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
- JavaScript無効時にもサンプル一覧を読めるSSR-first HTML
- 1 document / 1 bundle / 1 mount の単一ページ
- `jp-go-digital-design-system` と `--hig-*` token contract によるUI

`localStorage` 投稿は **この端末だけの掲載デモ**です。ネットワーク公開、本人確認、
通報、モデレーション、決済、応募、契約はまだ接続していません。

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
clojure -M:test
clojure -M:render public/index.html
clojure -M:build
```

生成した `public/index.html` を静的HTTPサーバで開くと動作を確認できます。

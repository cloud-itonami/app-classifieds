(ns classifieds.view
  "jp-go-ddsで描く、地域分類掲示板のSSR-first単一ページ。"
  (:require [clojure.string :as str]
            [classifieds.domain :as domain]
            [jp-go-dds.core :as dds]
            [jp-go-dds.page :as page]
            [jp-go-dds.tokens :as tokens]))

(def app-css
  (str/join
   "\n"
   [".classifieds-header { border-bottom: var(--hig-hairline) solid var(--hig-color-separator); background: var(--hig-color-system-background); position: sticky; top: 0; z-index: 10; }"
    ".classifieds-header__inner { display: flex; gap: var(--hig-spacing-4); align-items: center; justify-content: space-between; padding-block: var(--hig-spacing-3); }"
    ".classifieds-brand { color: var(--hig-color-label); text-decoration: none; font-weight: 700; }"
    ".classifieds-hero { background: var(--hig-color-secondary-system-grouped-background); }"
    ".classifieds-lede { color: var(--hig-color-secondary-label); max-width: 48rem; }"
    ".classifieds-filter-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr)); gap: var(--hig-spacing-4); align-items: end; }"
    ".classifieds-category-nav { display: flex; flex-wrap: wrap; gap: var(--hig-spacing-2); margin-block: var(--hig-spacing-4); }"
    ".classifieds-category-nav .dads-button[aria-pressed=\"true\"] { box-shadow: inset 0 0 0 var(--hig-hairline) var(--hig-color-tint); }"
    ".classifieds-meta { color: var(--hig-color-secondary-label); font-size: var(--hig-text-footnote-font-size); line-height: var(--hig-text-footnote-line-height); }"
    ".classifieds-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 18rem), 1fr)); gap: var(--hig-spacing-4); }"
    ".listing-card { display: flex; flex-direction: column; gap: var(--hig-spacing-3); }"
    ".listing-card[hidden] { display: none; }"
    ".listing-card__top { display: flex; gap: var(--hig-spacing-2); justify-content: space-between; align-items: start; }"
    ".listing-card__price { font-weight: 700; color: var(--hig-color-label); }"
    ".listing-card__source { overflow-wrap: anywhere; }"
    ".classifieds-empty { padding: var(--hig-spacing-8); text-align: center; color: var(--hig-color-secondary-label); }"
    ".classifieds-dialog { border: 0; border-radius: var(--hig-radius-lg); padding: 0; width: min(42rem, calc(100% - var(--hig-spacing-6))); max-height: calc(100% - var(--hig-spacing-6)); }"
    ".classifieds-dialog::backdrop { background: color-mix(in srgb, var(--hig-color-label) 45%, transparent); }"
    ".classifieds-dialog__body { padding: var(--hig-spacing-6); }"
    ".classifieds-dialog__actions { display: flex; flex-wrap: wrap; gap: var(--hig-spacing-3); justify-content: flex-end; }"
    ".classifieds-status { min-height: var(--hig-spacing-6); color: var(--hig-color-secondary-label); }"
    "@media (max-width: 40rem) { .classifieds-header__inner { align-items: stretch; flex-direction: column; } .classifieds-header__inner .dads-button { width: 100%; } }"
    "@media (prefers-reduced-motion: reduce) { .classifieds-dialog { scroll-behavior: auto; } }"]))

(defn- category-label [category-id]
  (:label (domain/category category-id)))

(defn listing-card [{:keys [id category region city title price age description source]}]
  [:article {:class "dds-ext-card listing-card"
             :data-listing-id id
             :data-category (name category)
             :data-region (name region)
             :data-search (domain/normalized-text {:title title :description description
                                                   :city city :price price})}
   [:div {:class "listing-card__top"}
    (dds/chip-label (category-label category))
    [:span {:class "classifieds-meta"} age]]
   (dds/heading 3 title {:size "24"})
   [:p {:class "listing-card__price"} price]
   [:p description]
   [:p {:class "classifieds-meta"} city]
   [:p {:class "classifieds-meta listing-card__source"} "出所: " source]])

(defn- category-button [{:keys [id label]} count]
  (dds/button (str label " " count)
              {:type :outline :size "sm"
               :attrs {:data-category-filter (name id)
                       :aria-pressed "false"}}))

(defn- post-dialog []
  [:dialog {:id "post-dialog" :class "classifieds-dialog" :aria-labelledby "post-dialog-title"}
   [:form {:id "post-form" :class "classifieds-dialog__body" :method "dialog"}
    (dds/heading 2 "地域に掲載する" {:id "post-dialog-title"})
    [:p {:class "classifieds-lede"}
     "この段階では、この端末のブラウザだけに保存される掲載デモです。ネットには公開されません。"]
    (dds/stack
     (dds/form-field {:label "タイトル" :for "post-title" :requirement "必須" :required? true}
                     (dds/input-text {:id "post-title" :name "title" :required true :maxlength 80}))
     (dds/form-field {:label "カテゴリ" :for "post-category" :requirement "必須" :required? true}
                     (dds/select {:id "post-category" :name "category" :required true}
                                 (into [["" "選択してください"]]
                                       (map (fn [{:keys [id label]}] [(name id) label]) domain/categories))))
     (dds/form-field {:label "地域" :for "post-region" :requirement "必須" :required? true}
                     (dds/select {:id "post-region" :name "region" :required true}
                                 (mapv (fn [{:keys [id label]}]
                                         [(if (= id :all) "" (name id))
                                          (if (= id :all) "選択してください" label)])
                                       domain/regions)))
     (dds/form-field {:label "市区町村" :for "post-city" :requirement "必須" :required? true}
                     (dds/input-text {:id "post-city" :name "city" :required true :maxlength 60}))
     (dds/form-field {:label "価格・報酬・家賃" :for "post-price" :requirement "任意"}
                     (dds/input-text {:id "post-price" :name "price" :maxlength 60
                                      :placeholder "例: 3,000円、時給1,200円、応相談"}))
     (dds/form-field {:label "説明" :for "post-description" :requirement "必須" :required? true}
                     (dds/textarea {:id "post-description" :name "description" :required true :maxlength 500 :rows 5}))
     [:p {:id "post-status" :class "classifieds-status" :role "status" :aria-live "polite"}]
     [:div {:class "classifieds-dialog__actions"}
      (dds/button "キャンセル" {:type :outline :attrs {:id "post-cancel"}})
      (dds/button "この端末に掲載" {:submit? true :attrs {:id "post-submit"}})])]])

(defn body [listings]
  (let [counts (domain/counts listings {:region :all :query ""})]
    [:div
     [:header {:class "classifieds-header"}
      (dds/container
       [:div {:class "classifieds-header__inner"}
        [:a {:class "classifieds-brand" :href "#top"} "まちかど掲示板"]
        (dds/button "掲載する" {:attrs {:id "open-post-dialog"}})])]
     [:main {:id "top"}
      [:div {:class "classifieds-hero"}
       (dds/container
        (dds/section
         {}
         (dds/heading 1 "近くで、見つける。つながる。")
         [:p {:class "classifieds-lede"}
          "売買・求人・住居・サービスを、地域からひとつの掲示板で探せます。"]
         [:div {:class "classifieds-filter-grid"}
          (dds/form-field {:label "地域" :for "region-filter"}
                          (dds/select {:id "region-filter" :name "region" :value "all"}
                                      (mapv (fn [{:keys [id label]}] [(name id) label]) domain/regions)))
          (dds/form-field {:label "キーワード" :for "query-filter"
                           :support "タイトル・説明・市区町村を検索"}
                          (dds/input-text {:id "query-filter" :name "query" :type "search"
                                           :placeholder "自転車、短期、家具付き…"}))]))]
      (dds/container
       (dds/section
        {:title "新着の掲載" :id "listings"}
        [:div {:class "classifieds-category-nav" :aria-label "カテゴリで絞り込む"}
         (dds/button (str "すべて " (:all counts))
                     {:type :outline :size "sm"
                      :attrs {:data-category-filter "all" :aria-pressed "true"}})
         (for [category domain/categories]
           (category-button category (get counts (:id category))))]
        [:p {:class "classifieds-meta"}
         [:span {:id "result-count"} (count listings)] "件を表示"]
        (into [:div {:id "listing-grid" :class "classifieds-list"}]
              (map listing-card listings))
        [:p {:id "empty-state" :class "classifieds-empty" :hidden true}
         "条件に合う掲載はありません。地域やキーワードを変えてください。"])
       (dds/section
        {:title "この掲示板について"}
        (dds/notification-banner
         {:type :info-1 :heading "4つの既存領域を、ひとつの発見面へ"}
         [:p "売買・求人・住居・サービスの原本と取引は各source repoが持ち、このアプリは地域別の検索・発見・投稿入口を束ねます。"])
        [:p {:class "classifieds-meta"}
         "サンプル掲載は接続契約の確認用です。実在する募集・物件・サービスではありません。"]))]
     (post-dialog)]))

(defn render [listings css]
  (page/->page
   {:title "まちかど掲示板 — 地域の売買・求人・住居・サービス"
    :description "地域別の売買・求人・住居・サービスをひとつの画面で探せる分類掲示板。"
    :lang "ja"
    :css css
    :app-css (str tokens/bridge-css "\n" app-css)
   :head [[:script {:defer true :src "app.js"}]]}
   (body listings)))

(defn render-404 [css]
  (page/->page
   {:title "ページが見つかりません — まちかど掲示板"
    :description "指定されたページは見つかりませんでした。"
    :lang "ja"
    :css css
    :app-css (str tokens/bridge-css "\n" app-css)}
   (dds/container
    (dds/section
     {}
     (dds/heading 1 "ページが見つかりません")
     [:p {:class "classifieds-lede"} "URLを確認するか、掲示板の最初の画面へ戻ってください。"]
     (dds/button "掲示板へ戻る" {:href "./"})))))

(ns classifieds.domain
  "地域分類掲示板の純粋な表示契約と検索判断。I/Oを持たない。"
  (:require [clojure.string :as str]))

(def categories
  [{:id :sale :label "売買" :source "cloud-itonami/fleamarket"}
   {:id :jobs :label "求人" :source "cloud-itonami/shigotoba"}
   {:id :housing :label "住居" :source "cloud-itonami/real-estate"}
   {:id :services :label "サービス" :source "cloud-itonami/hc"}])

(def regions
  [{:id :all :label "すべての地域"}
   {:id :hokkaido :label "北海道"}
   {:id :tohoku :label "東北"}
   {:id :kanto :label "関東"}
   {:id :chubu :label "中部"}
   {:id :kinki :label "近畿"}
   {:id :chugoku-shikoku :label "中国・四国"}
   {:id :kyushu-okinawa :label "九州・沖縄"}])

(def sample-listings
  [{:id "sale-tokyo-bike" :category :sale :region :kanto :city "世田谷区"
    :title "通勤用クロスバイク" :price "18,000円" :age "12分前"
    :description "室内保管。受け渡し場所は相談できます。"
    :source "cloud-itonami/fleamarket"}
   {:id "job-sendai-kitchen" :category :jobs :region :tohoku :city "仙台市"
    :title "週3日からのキッチンスタッフ" :price "時給1,180円" :age "38分前"
    :description "夕方から。未経験可、交通費支給。"
    :source "cloud-itonami/shigotoba"}
   {:id "housing-osaka-room" :category :housing :region :kinki :city "大阪市北区"
    :title "駅徒歩6分・家具付き1K" :price "月額68,000円" :age "1時間前"
    :description "短期相談可。初期費用と契約条件を明記しています。"
    :source "cloud-itonami/real-estate"}
   {:id "service-nagoya-repair" :category :services :region :chubu :city "名古屋市"
    :title "自転車の出張パンク修理" :price "2,500円から" :age "2時間前"
    :description "市内中心部へ伺います。部品代は事前に提示します。"
    :source "cloud-itonami/hc"}
   {:id "sale-sapporo-desk" :category :sale :region :hokkaido :city "札幌市"
    :title "無垢材のワークデスク" :price "12,000円" :age "今日"
    :description "幅120cm。車で取りに来られる方を希望。"
    :source "cloud-itonami/fleamarket"}
   {:id "job-fukuoka-design" :category :jobs :region :kyushu-okinawa :city "福岡市"
    :title "地域イベントのデザイン補助" :price "日給12,000円" :age "今日"
    :description "2日間の短期。Figma経験者歓迎。"
    :source "cloud-itonami/shigotoba"}
   {:id "housing-hiroshima-share" :category :housing :region :chugoku-shikoku :city "広島市"
    :title "庭のあるシェアハウス個室" :price "月額42,000円" :age "昨日"
    :description "光熱費別。内覧日時を相談できます。"
    :source "cloud-itonami/real-estate"}
   {:id "service-yokohama-lesson" :category :services :region :kanto :city "横浜市"
    :title "初心者向け写真レッスン" :price "90分4,000円" :age "昨日"
    :description "街歩きをしながらカメラの基本を練習します。"
    :source "cloud-itonami/hc"}])

(defn category [id]
  (some #(when (= id (:id %)) %) categories))

(defn normalized-text [listing]
  (->> [(:title listing) (:description listing) (:city listing) (:price listing)]
       (remove nil?)
       (str/join " ")
       str/lower-case))

(defn visible?
  [listing {:keys [region category query]
            :or {region :all category :all query ""}}]
  (let [q (str/lower-case (str/trim query))]
    (and (or (= region :all) (= region (:region listing)))
         (or (= category :all) (= category (:category listing)))
         (or (str/blank? q) (str/includes? (normalized-text listing) q)))))

(defn filter-listings [listings filters]
  (filterv #(visible? % filters) listings))

(defn counts [listings filters]
  (let [without-category (dissoc filters :category)]
    (into {:all (count (filter-listings listings without-category))}
          (map (fn [{:keys [id]}]
                 [id (count (filter-listings listings
                                             (assoc without-category :category id)))])
               categories))))

(defn validate-draft [{:keys [title city region description] category-id :category}]
  (cond-> {}
    (str/blank? (or title "")) (assoc :title "タイトルを入力してください")
    (str/blank? (or city "")) (assoc :city "市区町村を入力してください")
    (nil? (category category-id)) (assoc :category "カテゴリを選択してください")
    (not (some #(= region (:id %)) (rest regions))) (assoc :region "地域を選択してください")
    (str/blank? (or description "")) (assoc :description "説明を入力してください")))

(ns classifieds.browser
  "単一ページの絞り込みとローカル掲載。ネットワークI/Oは行わない。"
  (:require [kotoba.lang.text :as str]
            [kotoba.signal.ratchet :as ratchet]
            [kotoba.signal.x3dh :as x3dh]))

(def storage-key "cloud-itonami.app-classifieds.local-listings.v1")
(defonce filters (atom {:region "all" :category "all" :query ""}))
(defonce consents (atom #{}))
(defonce private-thread-ready? (atom false))

(defn by-id [id] (.getElementById js/document id))
(defn all [selector] (array-seq (.querySelectorAll js/document selector)))

(defn visible-card? [el]
  (let [{:keys [region category query]} @filters
        q (str/lower (str/trim query))]
    (and (or (= region "all") (= region (.getAttribute el "data-region")))
         (or (= category "all") (= category (.getAttribute el "data-category")))
         (or (str/blank? q) (str/includes? (.getAttribute el "data-search") q)))))

(defn apply-filters! []
  (let [cards (all ".listing-card")
        visible (reduce (fn [n el]
                          (let [show? (visible-card? el)]
                            (set! (.-hidden el) (not show?))
                            (if show? (inc n) n)))
                        0 cards)]
    (set! (.-textContent (by-id "result-count")) (str visible))
    (set! (.-hidden (by-id "empty-state")) (pos? visible))
    (doseq [button (all "[data-category-filter]")]
      (.setAttribute button "aria-pressed"
                     (str (= (:category @filters) (.getAttribute button "data-category-filter")))))))

(defn text-el [tag class-name text]
  (let [el (.createElement js/document tag)]
    (when class-name (set! (.-className el) class-name))
    (set! (.-textContent el) text)
    el))

(def category-labels
  {"sale" "売買" "jobs" "求人" "housing" "住居" "services" "サービス"})

(def kind-labels {"need" "Need" "seed" "Seed"})

(defn append-local-card! [listing]
  (let [article (.createElement js/document "article")
        top (.createElement js/document "div")]
    (set! (.-className article) "dds-ext-card listing-card")
    (.setAttribute article "data-listing-id" (str "local-" (:created-at listing)))
    (.setAttribute article "data-category" (:category listing))
    (.setAttribute article "data-region" (:region listing))
    (.setAttribute article "data-search"
                   (str/lower (str/join " " (map #(get listing %) [:title :description :city :price]))))
    (set! (.-className top) "listing-card__top")
    (.append top (text-el "span" "dads-chip-label"
                          (str (get kind-labels (:kind listing) "掲載") " · "
                               (get category-labels (:category listing)))))
    (.append top (text-el "span" "classifieds-meta" "この端末"))
    (.append article top)
    (.append article (text-el "h3" "dads-heading" (:title listing)))
    (.append article (text-el "p" "listing-card__price" (or (:price listing) "応相談")))
    (.append article (text-el "p" nil (:description listing)))
    (.append article (text-el "p" "classifieds-meta" (:city listing)))
    (.append article (text-el "p" "classifieds-meta listing-card__source" "出所: ローカル掲載デモ"))
    (.prepend (by-id "listing-grid") article)))

(defn stored-listings []
  (try
    (let [raw (.getItem js/localStorage storage-key)]
      (if raw (js->clj (js/JSON.parse raw) :keywordize-keys true) []))
    (catch :default _ [])))

(defn persist! [listings]
  (.setItem js/localStorage storage-key (js/JSON.stringify (clj->js listings))))

(defn form-value [form field]
  (.-value (.querySelector form (str "[name='" field "']"))))

(defn submit-post! [event]
  (.preventDefault event)
  (let [form (.-currentTarget event)
        listing {:title (str/trim (form-value form "title"))
                 :kind (form-value form "kind")
                 :category (form-value form "category")
                 :region (form-value form "region")
                 :city (str/trim (form-value form "city"))
                 :price (str/trim (form-value form "price"))
                 :description (str/trim (form-value form "description"))
                 :created-at (.now js/Date)}]
    (if (every? (complement str/blank?) ((juxt :title :category :region :city :description) listing))
      (let [next-listings (conj (vec (stored-listings)) listing)]
        (persist! next-listings)
        (append-local-card! listing)
        (.reset form)
        (.close (by-id "post-dialog"))
        (reset! filters {:region "all" :category "all" :query ""})
        (set! (.-value (by-id "region-filter")) "all")
        (set! (.-value (by-id "query-filter")) "")
        (apply-filters!))
      (set! (.-textContent (by-id "post-status")) "必須項目を入力してください。"))))

(defn bytes->hex [^js bytes]
  (apply str (map #(let [h (.toString % 16)] (if (= 1 (count h)) (str "0" h) h))
                  (array-seq bytes))))

(defn text-bytes [s] (.encode (js/TextEncoder.) s))

(defn random-token []
  (str "shipcap_" (bytes->hex (doto (js/Uint8Array. 12) js/crypto.getRandomValues))))

(defn set-state! [id state]
  (set! (.. (by-id id) -dataset -state) state))

(defn refresh-consent! []
  (let [n (count @consents)
        ready? (= n 2)]
    (set! (.-textContent (by-id "consent-status")) (str n " / 2人が同意"))
    (set-state! "consent-step" (if ready? "complete" "waiting"))
    (set! (.-disabled (by-id "open-private-thread")) (not ready?))
    (when ready?
      (set! (.-textContent (by-id "signal-status")) "双方同意済み。暗号化往復を検証できます。"))))

(defn consent! [party]
  (swap! consents conj party)
  (refresh-consent!))

(defn signal-roundtrip! []
  (let [status (by-id "signal-status")
        alice (x3dh/generate-identity)
        bob (x3dh/generate-identity)
        [bundle _] (x3dh/publish-bundle bob)
        plaintext "紹介を受け取りました。受け渡し条件を相談したいです。"]
    (set! (.-textContent status) "X3DH鍵合意を検証中…")
    (if-not (x3dh/verify-bundle bundle)
      (set! (.-textContent status) "署名済みprekeyの検証に失敗しました。")
      (-> (x3dh/x3dh-initiate alice bundle)
          (.then (fn [{:keys [shared-secret ek-pub opk-id] :as initiated}]
                   (-> (x3dh/x3dh-respond bob (:pub (:ik alice)) ek-pub opk-id)
                       (.then (fn [receiver-secret] [initiated shared-secret receiver-secret])))))
          (.then (fn [[_ sender-secret receiver-secret]]
                   (when-not (ratchet/bytes= sender-secret receiver-secret)
                     (throw (js/Error. "X3DH shared secrets differ")))
                   (js/Promise.all
                    #js [(ratchet/init-sender sender-secret (:spk bundle))
                         (ratchet/init-receiver receiver-secret (:spk bob))])))
          (.then (fn [states]
                   (let [sender (aget states 0)
                         receiver (aget states 1)]
                     (-> (ratchet/encrypt-message sender (text-bytes plaintext))
                         (.then (fn [[_ envelope]]
                                  (-> (ratchet/decrypt-message receiver envelope)
                                      (.then (fn [[_ decrypted]]
                                               {:envelope envelope
                                                :decrypted (.decode (js/TextDecoder.) decrypted)})))))))))
          (.then (fn [{:keys [envelope decrypted]}]
                   (when-not (= plaintext decrypted)
                     (throw (js/Error. "Double Ratchet plaintext mismatch")))
                   (reset! private-thread-ready? true)
                   (set-state! "message-step" "complete")
                   (set! (.-disabled (by-id "prepare-private-shipment")) false)
                   (set! (.-disabled (by-id "prepare-escrow")) false)
                   (set! (.-textContent status)
                         (str "X3DH + Double Ratchet往復成功 · ciphertext "
                              (.-length (:ciphertext envelope)) " bytes"))
                   (set! (.-textContent (by-id "transaction-status"))
                         "秘匿スレッド確認済み。配送とエスクローを準備できます。")))
          (.catch (fn [error]
                    (set! (.-textContent status) (str "暗号化検証失敗: " (.-message error)))))))))

(defn prepare-shipment! []
  (when @private-thread-ready?
    (let [token (random-token)
          output (by-id "privacy-output")]
      (set! (.-hidden output) false)
      (set! (.-textContent output)
            (str "配送capability: " token " · 開示先: 配送業者のみ · 住所: 公開面に保持しない"))
      (set! (.-textContent (by-id "transaction-status")) "匿名配送tokenを端末内で準備しました。配送予約は未実行です。")
      (set-state! "transaction-step" "complete"))))

(defn prepare-escrow! []
  (when @private-thread-ready?
    (let [output (by-id "privacy-output")]
      (set! (.-hidden output) false)
      (set! (.-textContent output)
            "Escrow proposal · JPY 18,000 · rail未選択 · 解除条件: provider capture + 配送完了 + 紛争なし + 名前付き人間承認")
      (set! (.-textContent (by-id "transaction-status")) "エスクロー提案を作成しました。資金移動は行っていません。")
      (set-state! "transaction-step" "complete"))))

(defn try-bot-intake! []
  (set! (.-textContent (by-id "bot-intake-status"))
        "accepted · kind=Need · source=public-feed.example · provenance保持 · 連絡は未実行"))

(defn init! []
  (doseq [listing (stored-listings)] (append-local-card! listing))
  (.addEventListener (by-id "region-filter") "change"
                     #(do (swap! filters assoc :region (.. % -target -value)) (apply-filters!)))
  (.addEventListener (by-id "query-filter") "input"
                     #(do (swap! filters assoc :query (.. % -target -value)) (apply-filters!)))
  (doseq [button (all "[data-category-filter]")]
    (.addEventListener button "click"
                       #(do (swap! filters assoc :category (.getAttribute (.-currentTarget %) "data-category-filter"))
                            (apply-filters!))))
  (.addEventListener (by-id "open-post-dialog") "click" #(.showModal (by-id "post-dialog")))
  (.addEventListener (by-id "post-cancel") "click" #(.close (by-id "post-dialog")))
  (.addEventListener (by-id "post-form") "submit" submit-post!)
  (.addEventListener (by-id "consent-need") "click" #(consent! :need))
  (.addEventListener (by-id "consent-seed") "click" #(consent! :seed))
  (.addEventListener (by-id "open-private-thread") "click" signal-roundtrip!)
  (.addEventListener (by-id "prepare-private-shipment") "click" prepare-shipment!)
  (.addEventListener (by-id "prepare-escrow") "click" prepare-escrow!)
  (.addEventListener (by-id "try-bot-intake") "click" try-bot-intake!)
  (refresh-consent!)
  (apply-filters!))

(defn ^:export main []
  (init!))

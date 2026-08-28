(ns classifieds.browser
  "単一ページの絞り込みとローカル掲載。ネットワークI/Oは行わない。"
  (:require [clojure.string :as str]))

(def storage-key "cloud-itonami.app-classifieds.local-listings.v1")
(defonce filters (atom {:region "all" :category "all" :query ""}))

(defn by-id [id] (.getElementById js/document id))
(defn all [selector] (array-seq (.querySelectorAll js/document selector)))

(defn visible-card? [el]
  (let [{:keys [region category query]} @filters
        data (.-dataset el)
        q (str/lower-case (str/trim query))]
    (and (or (= region "all") (= region (.-region data)))
         (or (= category "all") (= category (.-category data)))
         (or (str/blank? q) (str/includes? (.-search data) q)))))

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
                     (str (= (:category @filters) (.. button -dataset -categoryFilter)))))))

(defn text-el [tag class-name text]
  (let [el (.createElement js/document tag)]
    (when class-name (set! (.-className el) class-name))
    (set! (.-textContent el) text)
    el))

(def category-labels
  {"sale" "売買" "jobs" "求人" "housing" "住居" "services" "サービス"})

(defn append-local-card! [listing]
  (let [article (.createElement js/document "article")
        top (.createElement js/document "div")]
    (set! (.-className article) "dds-ext-card listing-card")
    (set! (.. article -dataset -listingId) (str "local-" (:created-at listing)))
    (set! (.. article -dataset -category) (:category listing))
    (set! (.. article -dataset -region) (:region listing))
    (set! (.. article -dataset -search)
          (str/lower-case (str/join " " (map #(get listing %) [:title :description :city :price]))))
    (set! (.-className top) "listing-card__top")
    (.append top (text-el "span" "dads-chip-label" (get category-labels (:category listing))))
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

(defn init! []
  (doseq [listing (stored-listings)] (append-local-card! listing))
  (.addEventListener (by-id "region-filter") "change"
                     #(do (swap! filters assoc :region (.. % -target -value)) (apply-filters!)))
  (.addEventListener (by-id "query-filter") "input"
                     #(do (swap! filters assoc :query (.. % -target -value)) (apply-filters!)))
  (doseq [button (all "[data-category-filter]")]
    (.addEventListener button "click"
                       #(do (swap! filters assoc :category (.. % -currentTarget -dataset -categoryFilter))
                            (apply-filters!))))
  (.addEventListener (by-id "open-post-dialog") "click" #(.showModal (by-id "post-dialog")))
  (.addEventListener (by-id "post-cancel") "click" #(.close (by-id "post-dialog")))
  (.addEventListener (by-id "post-form") "submit" submit-post!)
  (apply-filters!))

(.addEventListener js/window "DOMContentLoaded" init!)

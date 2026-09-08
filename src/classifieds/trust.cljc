(ns classifieds.trust
  "Need/Seed matching, consent, privacy shipment and escrow decisions. Pure: no
  network, key custody, carrier booking or money movement lives here."
  (:require [clojure.set :as set]
            [kotoba.lang.text :as str]))

(def supported-rails #{:stripe-connect :bank-transfer :x402-usdc})

(def sample-intents
  [{:id "need-bike-kyoto" :kind :need :category :sale :region :kinki
    :city "京都市" :actor-did "did:example:buyer-kyoto"
    :title "通学用の自転車を探しています"
    :terms #{:bicycle :pickup :budget-20000} :source "person/local"}
   {:id "seed-bike-osaka" :kind :seed :category :sale :region :kinki
    :city "大阪市" :actor-did "did:example:seller-osaka"
    :title "整備済みクロスバイクを譲れます"
    :terms #{:bicycle :pickup :budget-20000} :source "cloud-itonami/fleamarket"}
   {:id "need-photo-yokohama" :kind :need :category :services :region :kanto
    :city "横浜市" :actor-did "did:example:learner-yokohama"
    :title "週末に写真を教えてほしい"
    :terms #{:photo :weekend :beginner} :source "person/local"}
   {:id "seed-photo-yokohama" :kind :seed :category :services :region :kanto
    :city "横浜市" :actor-did "did:example:teacher-yokohama"
    :title "初心者向け写真レッスンができます"
    :terms #{:photo :weekend :beginner} :source "cloud-itonami/hc"}])

(defn valid-intent? [{:keys [id kind category region actor-did title source]}]
  (and (not-any? #(str/blank? (str %)) [id category region actor-did title source])
       (#{:need :seed} kind)
       (str/starts-with? actor-did "did:")))

(defn normalize-intake
  "Normalize one consented connector record. Provenance is mandatory; raw PII
  is deliberately not part of the returned public contract."
  [{:keys [id kind category region city actor-did title terms source observed-at]}]
  (let [intent {:id (str id)
                :kind (keyword kind)
                :category (keyword category)
                :region (keyword region)
                :city (str city)
                :actor-did (str actor-did)
                :title (str/trim (str title))
                :terms (set (map keyword terms))
                :source (str source)
                :observed-at observed-at}]
    (if (valid-intent? intent)
      {:status :accepted :intent intent}
      {:status :rejected :reason :invalid-or-unattributed-intent})))

(defn match-score [need seed]
  (let [shared (set/intersection (set (:terms need)) (set (:terms seed)))
        facts (cond-> []
                (= (:category need) (:category seed)) (conj {:reason :same-category :points 40})
                (= (:region need) (:region seed)) (conj {:reason :same-region :points 25})
                (= (:city need) (:city seed)) (conj {:reason :same-city :points 10})
                (seq shared) (conj {:reason :shared-terms :points (min 25 (* 10 (count shared)))
                                    :terms shared}))]
    {:score (reduce + (map :points facts)) :facts facts}))

(defn propose-match [need seed]
  (cond
    (not= :need (:kind need)) {:status :rejected :reason :left-must-be-need}
    (not= :seed (:kind seed)) {:status :rejected :reason :right-must-be-seed}
    (= (:actor-did need) (:actor-did seed)) {:status :rejected :reason :self-match}
    :else (let [{:keys [score facts]} (match-score need seed)]
            (if (< score 50)
              {:status :rejected :reason :insufficient-fit :score score :facts facts}
              {:id (str "match:" (:id need) ":" (:id seed))
               :status :proposed :role :ossekkai :score score :facts facts
               :participants #{(:actor-did need) (:actor-did seed)}
               :consents #{} :need-id (:id need) :seed-id (:id seed)}))))

(defn record-consent [proposal actor-did]
  (if (contains? (:participants proposal) actor-did)
    (update proposal :consents (fnil conj #{}) actor-did)
    (assoc proposal :status :rejected :reason :actor-not-a-participant)))

(defn open-introduction [proposal thread-id]
  (if (= (:participants proposal) (:consents proposal))
    (assoc proposal
           :status :introduced
           :role :nakoudo
           :thread {:id thread-id
                    :transport :signal-x3dh-double-ratchet
                    :purpose :match-negotiation
                    :participants (:participants proposal)})
    (assoc proposal :status :held :reason :bilateral-consent-required)))

(defn prepare-private-shipment
  "Return the public shipment capsule. The address is intentionally accepted
  nowhere: only a carrier-issued opaque capability may cross this boundary."
  [introduction {:keys [carrier capability-token expires-at]}]
  (cond
    (not= :introduced (:status introduction))
    {:status :held :reason :introduction-not-open}

    (or (str/blank? carrier) (str/blank? capability-token) (str/blank? expires-at))
    {:status :rejected :reason :carrier-capability-required}

    :else {:status :label-ready
           :shipment {:carrier carrier
                      :capability-token capability-token
                      :expires-at expires-at
                      :disclosed-to #{:carrier}
                      :address-present? false}}))

(defn plan-escrow [{:keys [match-id amount-minor currency rail]}]
  (cond
    (str/blank? match-id) {:status :rejected :reason :match-required}
    (not (and (integer? amount-minor) (pos? amount-minor)))
    {:status :rejected :reason :positive-integer-amount-required}
    (not (re-matches #"[A-Z]{3}" (or currency "")))
    {:status :rejected :reason :iso-currency-required}
    (not (contains? supported-rails rail))
    {:status :rejected :reason :unsupported-rail}
    :else {:id (str "escrow:" match-id)
           :status :funding-proposed
           :match-id match-id :amount-minor amount-minor :currency currency :rail rail
           :capture nil :delivery nil :disputed? false
           :release-requires #{:provider-capture :delivery-confirmed :named-human-authoriser}}))

(defn record-capture [escrow {:keys [provider-reference amount-minor]}]
  (if (and (= :funding-proposed (:status escrow))
           (not (str/blank? provider-reference))
           (= (:amount-minor escrow) amount-minor))
    (assoc escrow :status :funded
                  :capture {:provider-reference provider-reference :amount-minor amount-minor})
    (assoc escrow :status :held :reason :provider-capture-missing-or-mismatched)))

(defn record-delivery [escrow {:keys [carrier-reference delivered?]}]
  (if (and delivered? (not (str/blank? carrier-reference)))
    (assoc escrow :delivery {:carrier-reference carrier-reference :confirmed? true})
    (assoc escrow :status :held :reason :delivery-evidence-required)))

(defn propose-release [escrow {:keys [authorised-by]}]
  (cond
    (:disputed? escrow) (assoc escrow :status :held :reason :disputed-escrow)
    (not= :funded (:status escrow)) (assoc escrow :status :held :reason :funds-not-captured)
    (not (true? (get-in escrow [:delivery :confirmed?])))
    (assoc escrow :status :held :reason :delivery-not-confirmed)
    (str/blank? authorised-by) (assoc escrow :status :held :reason :named-human-authoriser-required)
    :else (assoc escrow :status :release-authorised
                        :release {:authorised-by authorised-by :effect :propose}
                        :money-moved? false)))

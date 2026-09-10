(ns classifieds.trust-test
  (:require [clojure.test :refer [deftest is testing]]
            [classifieds.trust :as trust]))

(def need (first trust/sample-intents))
(def seed (second trust/sample-intents))
(def proposal (trust/propose-match need seed))

(deftest intake-requires-provenance-and-did
  (is (= :accepted (:status (trust/normalize-intake need))))
  (is (= :rejected (:status (trust/normalize-intake (dissoc need :source)))))
  (is (= :rejected (:status (trust/normalize-intake (assoc need :actor-did "person-1"))))))

(deftest matching-is-directional-explainable-and-not-self-matching
  (is (= :proposed (:status proposal)))
  (is (= :ossekkai (:role proposal)))
  (is (>= (:score proposal) 50))
  (is (seq (:facts proposal)))
  (is (= :left-must-be-need (:reason (trust/propose-match seed need))))
  (is (= :self-match
         (:reason (trust/propose-match need (assoc seed :actor-did (:actor-did need)))))))

(deftest introduction-requires-both-parties
  (let [[a b] (vec (:participants proposal))
        one (trust/record-consent proposal a)
        held (trust/open-introduction one "thread-1")
        opened (-> one
                   (trust/record-consent b)
                   (trust/open-introduction "thread-1"))]
    (is (= :held (:status held)))
    (is (= :bilateral-consent-required (:reason held)))
    (is (= :introduced (:status opened)))
    (is (= :nakoudo (:role opened)))
    (is (= :signal-x3dh-double-ratchet (get-in opened [:thread :transport])))))

(deftest outsider-cannot-consent
  (let [result (trust/record-consent proposal "did:example:outsider")]
    (is (= :rejected (:status result)))
    (is (= :actor-not-a-participant (:reason result)))))

(deftest shipment-capsule-never-contains-address
  (let [opened (reduce trust/record-consent proposal (:participants proposal))
        intro (trust/open-introduction opened "thread-1")
        shipment (trust/prepare-private-shipment
                  intro {:carrier "carrier.example"
                         :capability-token "shipcap_abc"
                         :expires-at "2026-08-30T00:00:00Z"})]
    (is (= :label-ready (:status shipment)))
    (is (= #{:carrier} (get-in shipment [:shipment :disclosed-to])))
    (is (false? (get-in shipment [:shipment :address-present?])))
    (is (not-any? #{:address :postal-address} (tree-seq coll? seq shipment)))))

(deftest escrow-release-is-evidence-and-human-gated
  (let [planned (trust/plan-escrow {:match-id (:id proposal) :amount-minor 18000
                                    :currency "JPY" :rail :stripe-connect})
        no-capture (trust/propose-release planned {:authorised-by "human:operator"})
        funded (trust/record-capture planned {:provider-reference "pi_test_1" :amount-minor 18000})
        no-delivery (trust/propose-release funded {:authorised-by "human:operator"})
        delivered (trust/record-delivery funded {:carrier-reference "track_test_1" :delivered? true})
        no-human (trust/propose-release delivered {})
        authorised (trust/propose-release delivered {:authorised-by "human:operator"})]
    (is (= :funding-proposed (:status planned)))
    (is (= :funds-not-captured (:reason no-capture)))
    (is (= :delivery-not-confirmed (:reason no-delivery)))
    (is (= :named-human-authoriser-required (:reason no-human)))
    (is (= :release-authorised (:status authorised)))
    (is (false? (:money-moved? authorised)))
    (is (= :propose (get-in authorised [:release :effect])))))

(deftest disputed-escrow-never-releases
  (let [escrow (-> (trust/plan-escrow {:match-id (:id proposal) :amount-minor 1
                                       :currency "USD" :rail :x402-usdc})
                   (trust/record-capture {:provider-reference "chain:test" :amount-minor 1})
                   (trust/record-delivery {:carrier-reference "track:test" :delivered? true})
                   (assoc :disputed? true))]
    (is (= :disputed-escrow
           (:reason (trust/propose-release escrow {:authorised-by "human:operator"}))))))

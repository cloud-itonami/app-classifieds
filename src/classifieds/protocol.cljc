(ns classifieds.protocol
  "Public discovery contracts. They describe locally verified capabilities and
  explicitly do not claim a deployed A2A transport, carrier or payment rail."
  (:require [classifieds.trust :as trust]))

(def mcp-tools
  [{:name "classifieds.search"
    :description "Search public classifieds and Need/Seed intents."
    :inputSchema {:type "object" :properties {:query {:type "string"}
                                               :region {:type "string"}}}}
   {:name "classifieds.intake.normalize"
    :description "Normalize one consented, provenance-bearing Need or Seed record."
    :inputSchema {:type "object"
                  :required ["id" "kind" "category" "region" "actorDid" "title" "source"]
                  :properties {:id {:type "string"}
                               :kind {:type "string" :enum ["need" "seed"]}
                               :category {:type "string"}
                               :region {:type "string"}
                               :actorDid {:type "string"}
                               :title {:type "string"}
                               :source {:type "string"}}}}
   {:name "classifieds.match.propose"
    :description "Create an explainable ossekkai match proposal; it never contacts parties."
    :inputSchema {:type "object" :required ["needId" "seedId"]
                  :properties {:needId {:type "string"} :seedId {:type "string"}}}}])

(def agent-card
  {:name "Cloud Itonami Classifieds Match Agent"
   :description "Consent-bound Need/Seed discovery and explainable introductions."
   :url "urn:cloud-itonami:app-classifieds:local-mcp"
   :version "0.2.0"
   :capabilities {:streaming false :pushNotifications false}
   :skills [{:id "need-seed-intake" :name "Need/Seed intake" :tags ["classifieds" "intake"]}
            {:id "ossekkai-match" :name "Ossekkai matching" :tags ["matching" "consent"]}
            {:id "nakoudo-introduction" :name "Nakoudo introduction" :tags ["introduction" "privacy"]}]
   :x-transport-status "local-mcp-verified-a2a-http-not-deployed"})

(def web3-binding
  {:version 1
   :identity "participant-supplied DID"
   :public-records ["listing" "need" "seed" "match-proposal"]
   :content-addressing {:algorithm "sha2-256" :status "browser-generated"}
   :payment-rails (mapv name (sort trust/supported-rails))
   :never-public ["postal-address" "message-plaintext" "private-key" "payment-credential"]
   :status "contract-implemented-no-chain-write-performed"})

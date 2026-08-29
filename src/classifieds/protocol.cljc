(ns classifieds.protocol
  "Public discovery contracts for the deployed read/proposal surface. External
  delivery, carrier booking and money movement remain explicitly excluded."
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
    :inputSchema {:type "object" :required ["need" "seed"]
                  :properties {:need {:type "object"} :seed {:type "object"}}}}])

(def agent-card
  {:name "Cloud Itonami Classifieds Match Agent"
   :description "Consent-bound Need/Seed discovery and explainable introductions."
   :supportedInterfaces
   [{:url "https://cloud-itonami-app-classifieds-production.04-feasts-minded.workers.dev/a2a"
     :protocolBinding "JSONRPC" :protocolVersion "1.0"}
    {:url "https://cloud-itonami-app-classifieds-production.04-feasts-minded.workers.dev"
     :protocolBinding "HTTP+JSON" :protocolVersion "1.0"}]
   :provider {:organization "cloud-itonami"
              :url "https://github.com/cloud-itonami/app-classifieds"}
   :version "0.3.0"
   :documentationUrl "https://github.com/cloud-itonami/app-classifieds"
   :capabilities {:streaming false :pushNotifications false :extendedAgentCard false}
   :defaultInputModes ["text/plain" "application/json"]
   :defaultOutputModes ["application/json"]
   :skills [{:id "need-seed-intake" :name "Need/Seed intake"
             :description "Normalize consented, provenance-bearing demand and supply records."
             :tags ["classifieds" "intake"]}
            {:id "ossekkai-match" :name "Ossekkai matching"
             :description "Propose explainable matches without contacting either party."
             :tags ["matching" "consent"]}
            {:id "nakoudo-introduction" :name "Nakoudo introduction"
             :description "Describe the bilateral-consent gate for a private introduction."
             :tags ["introduction" "privacy"]}]
   :x-effects {:contactsParties false :movesMoney false :booksCarrier false}
   :x-transport-status "production-http-a2a-and-mcp-deployed"})

(def web3-binding
  {:version 1
   :identity "participant-supplied DID"
   :public-records ["listing" "need" "seed" "match-proposal"]
   :content-addressing {:algorithm "sha2-256" :status "browser-generated"}
   :payment-rails (mapv name (sort trust/supported-rails))
   :never-public ["postal-address" "message-plaintext" "private-key" "payment-credential"]
   :status "contract-implemented-no-chain-write-performed"})

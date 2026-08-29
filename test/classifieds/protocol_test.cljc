(ns classifieds.protocol-test
  (:require [clojure.test :refer [deftest is]]
            [classifieds.protocol :as protocol]))

(deftest discovery-contracts-are-honest
  (is (= 3 (count protocol/mcp-tools)))
  (is (= "local-mcp-verified-a2a-http-not-deployed"
         (:x-transport-status protocol/agent-card)))
  (is (= "contract-implemented-no-chain-write-performed"
         (:status protocol/web3-binding)))
  (is (some #{"postal-address"} (:never-public protocol/web3-binding))))

(ns classifieds.protocol-test
  (:require [clojure.test :refer [deftest is]]
            [classifieds.protocol :as protocol]))

(deftest discovery-contracts-are-honest
  (is (= 3 (count protocol/mcp-tools)))
  (is (= "production-http-a2a-and-mcp-deployed"
         (:x-transport-status protocol/agent-card)))
  (is (= "1.0" (get-in protocol/agent-card [:supportedInterfaces 0 :protocolVersion])))
  (is (false? (get-in protocol/agent-card [:x-effects :movesMoney])))
  (is (= "contract-implemented-no-chain-write-performed"
         (:status protocol/web3-binding)))
  (is (some #{"postal-address"} (:never-public protocol/web3-binding))))

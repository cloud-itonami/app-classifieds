(ns classifieds.domain-test
  (:require [clojure.test :refer [deftest is testing]]
            [classifieds.domain :as domain]))

(deftest four-domain-sources-are-explicit
  (is (= #{:sale :jobs :housing :services} (set (map :id domain/categories))))
  (is (= #{"cloud-itonami/fleamarket" "cloud-itonami/shigotoba"
           "cloud-itonami/real-estate" "cloud-itonami/hc"}
         (set (map :source domain/categories)))))

(deftest filters-compose
  (is (= 2 (count (domain/filter-listings domain/sample-listings {:region :kanto}))))
  (is (= ["job-sendai-kitchen"]
         (mapv :id (domain/filter-listings domain/sample-listings
                                           {:category :jobs :query "仙台"}))))
  (is (empty? (domain/filter-listings domain/sample-listings
                                      {:region :hokkaido :category :housing}))))

(deftest counts-ignore-current-category-but-respect-region-and-query
  (let [counts (domain/counts domain/sample-listings {:region :kanto :category :sale :query ""})]
    (is (= 2 (:all counts)))
    (is (= 1 (:sale counts)))
    (is (= 1 (:services counts)))
    (is (= 0 (:jobs counts)))))

(deftest draft-validation
  (is (= #{} (set (keys (domain/validate-draft
                         {:title "机" :city "札幌市" :category :sale :region :hokkaido
                          :description "取りに来られる方"})))))
  (is (= #{:title :city :category :region :description}
         (set (keys (domain/validate-draft {}))))))

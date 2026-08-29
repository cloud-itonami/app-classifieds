(ns classifieds.view-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [classifieds.domain :as domain]
            [classifieds.view :as view]))

(deftest page-is-one-accessible-classifieds-document
  (let [html (view/render domain/sample-listings "/* dds */")]
    (is (str/starts-with? html "<!DOCTYPE html>"))
    (is (str/includes? html "width=device-width"))
    (is (str/includes? html "売買・求人・住居・サービス"))
    (is (= 1 (count (re-seq #"<script" html))))
    (is (str/includes? html "src=\"js/app.js\""))
    (is (str/includes? html "aria-live=\"polite\""))
    (is (str/includes? html "AIおせっかいから、信頼できる取引へ"))
    (is (str/includes? html "X3DH"))
    (is (str/includes? html "classifieds.intake.normalize"))
    (is (str/includes? html "サンプル掲載は接続契約の確認用"))))

(deftest every-source-and-listing-is-rendered
  (let [html (view/render domain/sample-listings "")]
    (doseq [{:keys [source]} domain/categories]
      (is (str/includes? html source)))
    (doseq [{:keys [id title]} domain/sample-listings]
      (testing id
        (is (str/includes? html id))
        (is (str/includes? html title))))))

(deftest app-css-obeys-token-contract
  (is (not (re-find #"#[0-9a-fA-F]{3,8}" view/app-css)))
  (is (not (re-find #"font-size:\\s*[^v][^;]*px" view/app-css)))
  (is (str/includes? view/app-css "var(--hig-spacing-4)")))

(deftest not-found-is-a-relative-design-system-page
  (let [html (view/render-404 "/* dds */")]
    (is (str/includes? html "ページが見つかりません"))
    (is (str/includes? html "href=\"./\""))
    (is (str/includes? html "dads-button"))))

(ns classifieds.render
  "静的HTMLを生成するClojure互換entry。"
  (:require [classifieds.domain :as domain]
            [classifieds.protocol :as protocol]
            [classifieds.view :as view]
            #?(:clj [clojure.data.json :as json])
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (defn -main [& [output]]
     (let [target (or output "public/index.html")
           target-file (io/file target)
           not-found (io/file (.getParentFile target-file) "404.html")
           protocol-dir (io/file (.getParentFile target-file) "protocol")
           well-known-dir (io/file (.getParentFile target-file) ".well-known")
           css (slurp (io/resource "jp_go_dds/dds.css"))]
       (.mkdirs protocol-dir)
       (.mkdirs well-known-dir)
       (spit target (view/render domain/sample-listings css))
       (spit not-found (view/render-404 css))
       (spit (io/file protocol-dir "mcp-tools.json")
             (json/write-str {:tools protocol/mcp-tools} :escape-slash false))
       (spit (io/file protocol-dir "web3-binding.json")
             (json/write-str protocol/web3-binding :escape-slash false))
       (spit (io/file well-known-dir "agent-card.json")
             (json/write-str protocol/agent-card :escape-slash false))
       (println (str "rendered " target " listings=" (count domain/sample-listings)
                     " 404=" not-found " protocols=3")))))

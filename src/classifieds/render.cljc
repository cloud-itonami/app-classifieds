(ns classifieds.render
  "静的HTMLを生成するClojure互換entry。"
  (:require [classifieds.domain :as domain]
            [classifieds.view :as view]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (defn -main [& [output]]
     (let [target (or output "public/index.html")
           target-file (io/file target)
           not-found (io/file (.getParentFile target-file) "404.html")
           css (slurp (io/resource "jp_go_dds/dds.css"))]
       (spit target (view/render domain/sample-listings css))
       (spit not-found (view/render-404 css))
       (println (str "rendered " target " listings=" (count domain/sample-listings)
                     " 404=" not-found)))))

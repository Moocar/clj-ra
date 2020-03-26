(ns ra.main
  (:require ra.integrant))

(defn run []
  (ra.integrant/start-system))

(defn -main [& _]
  (run))

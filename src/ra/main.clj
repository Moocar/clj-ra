(ns ra.main
  (:require ra.integrant
            ra.log))

(defn run []
  (ra.integrant/start-system))

(defn -main [& _]
  (alter-var-root #'ra.log/*verbose* (constantly true))
  (run))

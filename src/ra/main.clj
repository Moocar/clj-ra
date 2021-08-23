(ns ra.main
  (:require ra.integrant
            ra.log))

(defn run []
  (ra.integrant/start-system))

(defn -main [& _]
  (with-redefs [ra.log/*verbose* true]
    (run)))

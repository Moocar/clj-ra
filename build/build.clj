(ns build
  (:require [clojure.tools.build.api :as b]))

(def public-dir "resources/public")
(def prod-user "anthony")
(def prod-machine "slang-service.bnr.la")
(def prod-home-dir (str "/home/" prod-user))
(def prod-deploy-dir (str prod-home-dir "/ra"))
(def ssh-str (format "%s@%s" prod-user prod-machine))

(defn yarn-install [_]
  (b/process {:command-args ["yarn" "install"]}))

(defn tailwind [{:keys [watch release]}]
  (let [base ["npx" "tailwindcss" "-i" "css/styles.css" "-o" (str public-dir "/styles.css")]]
    (b/process (cond-> {:command-args (concat base (cond watch ["--watch"] release ["--minify"]))}
                 release (assoc :env {"NODE_ENV" "production"})))))

(defn shadow-cljs [{:keys [release server]}]
  (cond release (b/process {:command-args ["npx" "shadow-cljs" "release" "main"]})
        server  (b/process {:command-args ["npx"
                                           "shadow-cljs"
                                           "-d" "nrepl/nrepl:0.8.3"
                                           "-d" "cider/piggieback:0.5.2"
                                           "-d" "refactor-nrepl/refactor-nrepl:2.5.1"
                                           "-d" "cider/cider-nrepl:0.26.0"
                                           "server"]})))

(defn dev [_]
  (future (tailwind {:watch true}))
  (future (shadow-cljs {:server true})))

(defn deploy [_]
  (b/process
   {:command-args ["rsync"
                   "-v"
                   "-r"
                   "--exclude" "node_modules/"
                   "--exclude" ".clj-kondo/"
                   "--exclude" ".git/"
                   "--exclude" ".cpcache/"
                   "--exclude" ".shadow-cljs/"
                   "--exclude" (str public-dir "/js/main/cljs-runtime")
                   "--exclude" ".cider-repl-history"
                   "--exclude" ".nrepl-port"
                   "--exclude" "report*.json"
                   "./"
                   (str ssh-str ":" prod-deploy-dir)]}))

(defn restart [_]
  (let [password (read-line)]
    (b/process
     {:command-args ["ssh"
                     ssh-str
                     "--"
                     (format "echo %s | sudo -S systemctl restart ra.service" password)]})))

(defn all [_]
  (yarn-install nil)
  (tailwind {:release true})
  (shadow-cljs {:release true})
  (deploy nil)
  (restart nil))

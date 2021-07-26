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

(defn tailwind [_]
  (b/process {:command-args ["npx" "tailwindcss" "-i" "css/styles.css" "-o" (str public-dir "/styles.css") " --minify"]
              :env {"NODE_ENV" "production"}}))

(defn shadow-cljs [_]
  (b/process {:command-args ["npx" "shadow-cljs" "release" "main"]}))

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
  (tailwind nil)
  (shadow-cljs nil)
  (deploy nil)
  (restart nil))

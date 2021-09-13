(ns build
  (:require [clojure.tools.build.api :as b]))

(def resources-dir "resources")
(def public-dir (str resources-dir "/public"))
(def prod-user "anthony")
(def prod-machine "slang-service.bnr.la")
(def prod-home-dir (str "/home/" prod-user))
(def prod-deploy-dir (str prod-home-dir "/ra"))
(def ssh-str (format "%s@%s" prod-user prod-machine))
(def version (format "1.0.%s" (b/git-count-revs nil)))

(defn capture-process [response]
  (let [{:keys [exit]} response]
    (when (not= 0 exit)
      (throw (ex-info "Process returned error" response)))))

(defn write-version [_]
  (b/write-file {:path (str resources-dir "/.version.edn")
                 :content {:version version}}))

(defn yarn-install [_]
  (capture-process (b/process {:command-args ["yarn" "install"]})))

(defn tailwind [{:keys [watch release]}]
  (let [base ["npx" "tailwindcss" "-i" "css/styles.css" "-o" (str public-dir "/styles.css")]]
    (capture-process
     (b/process (cond-> {:command-args (concat base (cond watch ["--watch"] release ["--minify"]))}
                  release (assoc :env {"NODE_ENV" "production"}))))))

(defn shadow-cljs [{:keys [release server]}]
  (capture-process
   (cond release (b/process {:command-args ["npx" "shadow-cljs" "release" "main"]})
         server  (b/process {:command-args ["npx" "shadow-cljs" "server"]}))))

(defn nrepl-server [{}]
  (capture-process
   (b/process {:command-args ["clojure"
                              "-A:server-dev:nrepl"
                              "-m" "nrepl.cmdline"
                              "--middleware" "[refactor-nrepl.middleware/wrap-refactor,cider.nrepl/cider-middleware]"]})))

(defn deploy [_]
  (capture-process
   (b/process
    {:command-args ["rsync"
                    "-v"
                    "-r"
                    "--delete"
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
                    (str ssh-str ":" prod-deploy-dir)]})))

(defn read-password [prompt]
  ;; Based on https://groups.google.com/forum/#!topic/clojure/ymDZj7T35x4
  (let [console (System/console)
        chars   (.readPassword console "%s" (into-array [prompt]))]
    (apply str chars)))

(defn restart [_]
  (let [password (read-password "Password: ")]
    (b/process
     {:command-args ["ssh"
                     ssh-str
                     "--"
                     (format "echo %s | sudo -S systemctl restart ra.service" password)]})))

(defn all [_]
  (write-version {})
  (yarn-install nil)
  (tailwind {:release true})
  (shadow-cljs {:release true})
  (deploy nil)
  (restart nil))

(defn dev [_]
  (write-version {})
  (future (tailwind {:watch true}))
  (future (nrepl-server {}))
  (future (shadow-cljs {:server true})))

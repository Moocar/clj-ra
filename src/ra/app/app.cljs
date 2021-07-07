(ns ra.app.app
  (:require [clojure.string :as str]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.networking.websockets :as fws]
            [edn-query-language.core :as eql]
            [ra.specs.game :as game]))

(defmutation hide-error [_]
  (action [env]
          (swap! (:state env) assoc :ui/error-occurred false)))

(defmutation show-error [_]
  (action [env]
    (swap! (:state env) assoc :ui/error-occurred true)
    (js/setTimeout #(comp/transact! (:app env) [(hide-error {})]) 3000)))

(def global-eql-transform
  (fn [ast]
    (let [mutation? (symbol? (:dispatch-key ast))]
      (cond-> (-> (app/default-global-eql-transform ast)
                  (update :children conj (eql/expr->ast :com.wsscode.pathom.core/errors)))
        mutation? (update :children conj (eql/expr->ast :tempids))))))

(defn response-mutation-error? [body]
  (and (= 1 (count body))
       (or (symbol? (key (first body)))
           (keyword? (key (first body))))
       (map? (val (first body)))
       (:error (val (first body)))))

(defn remote-error? [{:keys [body] :as result}]
  (or
   (app/default-remote-error? result)
   (map? (:com.wsscode.pathom.core/errors body))
   (response-mutation-error? body)))

(defn global-error-action
  "Run when app's :remote-error? returns true"
  [env]
  (let [{:keys [result]} env
        {:keys [body error-text]} result]
    (if (:com.wsscode.pathom.core/errors body)
      (let [pathom-errs (:com.wsscode.pathom.core/errors body)
            msg         (cond
                          (seq error-text)
                          error-text

                          pathom-errs
                          (->> pathom-errs
                               (map (fn [[query {{:keys [message data]} :com.fulcrologic.rad.pathom/errors :as val}]]
                                      (str query
                                           " failed with "
                                           (or (and message (str message (when (seq data) (str ", extra data: " data))))
                                               val))))
                               (str/join " | "))

                          :else
                          (str body))]
        ;; Store the error into the state for display:
        (swap! (:state env) assoc :ui/global-error msg))
      (let [{:keys [error]} (val (first body))]
        (swap! (:state env) assoc :ui/global-error (:msg error))))))

(defonce APP
  (app/fulcro-app
   {:remote-error?       remote-error?
    :global-error-action global-error-action
    :global-eql-transform global-eql-transform
    :remotes             {:remote (fws/fulcro-websocket-remote
                                   {:push-handler          (fn [{:keys [topic msg] :as all}]
                                                             (let [{:keys [::game/id]} msg]
                                                      (df/load! APP [::game/id id] (comp/registry-key->class :ra.app.game/Game))))
                                    :global-error-callback #(comp/transact! APP [(show-error {})])})}}))

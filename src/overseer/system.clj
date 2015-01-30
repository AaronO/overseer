(ns overseer.system
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [clj-yaml.core :as yaml]
            [datomic.api :as d]
            [taoensso.timbre :as timbre]
            (overseer
              [schema :as schema]
              [worker :as worker])))

(defn ->system [options]
  {:pre [(:config options)]}
  (let [config (yaml/parse-string (slurp (:config options)))
        datomic-uri (get-in config [:datomic :uri])]
    {:config config
     :conn (d/connect datomic-uri)}))

(defn start [job-handlers system]
  (let [worker-signal (atom :start)]
    (-> system
        (assoc-in [:worker :signal] worker-signal)
        (assoc-in [:worker :ref] (future
                                   (worker/start!
                                     system
                                     job-handlers
                                     worker-signal))))))

(defn deref-worker [system]
  (if-let [worker-ref (get-in system [:worker :ref])]
    @worker-ref
    (timbre/warn "Attempted to deref a nil worker")))

(defn stop [system]
  (worker/stop! (get-in system [:worker :signal]))
  (timbre/info "Signalled system stop; waiting for stop.")
  (deref-worker system))

(defn parse-ns
  "Given a handler path like \"myapp.core/my-handlers\", parse it
   to the symbol 'myapp.core"
  [handler-path]
  (->> (butlast (string/split handler-path #"/"))
       first
       symbol))

(def cli-options
  [["-c" "--config PATH" "Path to YAML configuration file"
    :default nil]])

(defn print-usage []
  (let [{:keys [summary]} (cli/parse-opts [] cli-options)]
    (println "Usage: overseer.system handlers [options]")
    (println summary)))

(defn -main
  [& args]
  (let [{:keys [options summary errors] :as opts}
        (cli/parse-opts args cli-options)]

    (if-not (.exists (io/file (:config options)))
      (do
        (print-usage)
        (System/exit 1)))

    (if-let [handlers-str (first (:arguments opts))]
      (do
        (require (parse-ns handlers-str))
        (start (eval (symbol handlers-str)) (->system options)))
      (do
        (print-usage)
        (System/exit 1)))))

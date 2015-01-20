(ns overseer.schema
  (:require [datomic.api :as d]))

 (def schema-txn
   [{:db/id (d/tempid :db.part/db)
     :db/ident :job/id
     :db/valueType :db.type/string
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one
     :db/doc "A job's unique ID (a semi-sequential UUID)"
     :db.install/_attribute :db.part/db}

    {:db/id (d/tempid :db.part/db)
     :db/ident :job/type
     :db/valueType :db.type/keyword
     :db/cardinality :db.cardinality/one
     :db/doc "A job's type, represented by a keyword"
     :db.install/_attribute :db.part/db}

    {:db/id (d/tempid :db.part/db)
     :db/ident :job/status
     :db/valueType :db.type/keyword
     :db/cardinality :db.cardinality/one
     :db/doc "A job's status (unstarted|started|aborted|failed|finished)"
     :db/index true
     :db.install/_attribute :db.part/db}

    {:db/id (d/tempid :db.part/db)
     :db/ident :job/dep
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/many
     :db/doc
     "Dependency of this job ('parent'). Refers to other jobs
     that must be completed before this job can run."
     :db.install/_attribute :db.part/db}

    {:db/id (d/tempid :db.part/db)
     :db/ident :job.status/updated-at
     :db/valueType :db.type/instant
     :db/cardinality :db.cardinality/one
     :db/doc "Time at which a job's status was last updated."
     :db.install/_attribute :db.part/db}])

(def reserve-job
  "Datomic database function to atomically reserve a job.
   Either reserves the given entity, or throws."
  {:db/id (d/tempid :db.part/user)
   :db/ident :reserve-job
   :db/fn (datomic.function/construct
            {:lang "clojure"
             :params '[db entity-id]
             :code
             '(let [result (datomic.api/q '[:find ?s
                                            :in $data ?e
                                            :where [$data ?e :job/status ?s]]
                             db
                             entity-id)
                    status (first (first result))]
                (if (= :unstarted status)
                  [[:db/add entity-id :job/status :started]
                   [:db/add entity-id :job.status/updated-at (java.util.Date.)]]
                  (throw (Exception. "Job status not eligible for start."))))})})

 (defn install
   "Install Overseer's schema and DB functions into Datomic"
   [conn]
   @(d/transact conn (conj schema-txn reserve-job))
   :ok)
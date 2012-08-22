(ns datomic-test.datomic-helpers
  (:use [datomic.api :only [q db] :as d]))

(defn new-attribute-spec [ident type cardinality]
  {:db/id (d/tempid :db.part/db)
   :db/ident ident
   :db/valueType type
   :db/cardinality cardinality
   :db.install/_attribute :db.part/db})

(ns datomic-test.core
  (:use [datomic.api :only [q db] :as d]
        [clojure.pprint]
        [datomic-test.datomic-helpers]))



;; Define the minimal datomic schema for a database containing only "thing"s
(def simple-schema
  [(new-attribute-spec :thing/name :db.type/string :db.cardinality/one)
   (new-attribute-spec :thing/value :db.type/long :db.cardinality/one)])

;; This is where my database will live
(def uri "datomic:mem://my-things")
;(def uri "datomic:free://localhost:4334/my-things")

;; (Re)create the database
(d/delete-database uri)
(d/create-database uri)

;; Now connect to it
(def conn (d/connect uri))

;; Install our schema into the new database
(do
  (try
    @(d/transact conn simple-schema)
    (catch Exception e (pprint e))))

;; Add a thing to the database
(do
  @(d/transact conn [{:db/id #db/id[:db.part/user]
                      :thing/name "My first thing"
                      :thing/value 42}]))


;; Function to return all things in a particular database state
(defn get-all-things [db-state]
  (let [all-things-ids (q '[:find ?t :where [?t :thing/name]] db-state)]
    (map #(d/entity db-state (first %)) all-things-ids)))

(defn print-all-current-things []
  (dorun
   (map #(println (:thing/name %) ":" (:thing/value %))
        (get-all-things (db conn)))))

;; Display all things in the database now
(print-all-current-things)

;; Add another thing to the database
(do
  @(d/transact conn [{:db/id #db/id[:db.part/user]
                      :thing/name "My second thing"
                      :thing/value 43}]))

;; Again, get all the things out of the database
(print-all-current-things)


;; Now we learn that there's a better way to get entities
;; In particular, we don't have to look up all the ids and then
;; get each entity in turn. We can query for multiple attributes

(defn get-all-things-more-elegantly [db-state]
  (let [things (q '[:find ?n ?v
                    :where [?t :thing/name ?n]
                           [?t :thing/value ?v]] db-state)]
    (map (fn [[name val]] {:name name :val val}) things)))

(pprint (get-all-things-more-elegantly (db conn)))


;; Get the times of all transactions on the database
(def transaction-times
  (map #(first %)
       (reverse (sort (q '[:find ?when
                           :where [?transaction :db/txInstant ?when]] (db conn))))))

;; Get the state of the database at the time when the
;; second-most-recent transaction happened. I.e. when we added the first thing.

(def previous-state (d/as-of (db conn) (second transaction-times)))

(pprint (get-all-things-more-elegantly previous-state))

;; So apparently d/as-of gives state including transactions that happened at that exact time.


;; Now let's try removing the first thing

(def first-thing-id
  (ffirst (q '[:find ?t
               :where [?t :thing/name "My first thing"]] (db conn))))
(do
  (try
    @(d/transact conn `[[:db.fn/retractEntity ~first-thing-id]])
    (catch Exception e (pprint e))))


;; Check that it's worked

(pprint (get-all-things-more-elegantly (db conn)))

;; Yay. I think that's enough for today.


;; Create a transaction function that can increase the value of an attribute
;; For some reason, #db/fn only works with def, not anywhere else.
;; Elsewhere, must use (datomic.function/construct '{...})
(def inc-fn
  #db/fn {:lang "clojure"
          :params [db id attr amount]
          :code (let [e    (datomic.api/entity db id)
                      orig (attr e 0) ]
                  [[:db/add id attr (+ orig amount)]])})

(do
  @(d/transact conn
               [{:db/id #db/id[:db.part/user]
                 :db/ident :inc
                 :db/fn inc-fn}]))


;; Now increment the value of the first "thing" still in the database

(def first-thing-id
  (ffirst (q '[:find ?t
               :where [?t :thing/name]] (db conn))))

(do
  @(d/transact conn
               [[:inc first-thing-id :thing/value 10]]))

;; See that it's worked

(pprint (get-all-things-more-elegantly (db conn)))

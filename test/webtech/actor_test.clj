(ns webtech.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [webtech.actor :as actor]
            [webtech.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-site! st {:site-id "S-1" :client-id "client-1"
                              :name "public-portal"
                              :min-conformance-level :aa
                              :approved-domains #{"portal.example.org"}})
    st))

(deftest commits-a-conforming-approved-domain-deployment
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-deployment :stake :low
                 :site-id "S-1" :achieved-conformance-level :aa
                 :domain "portal.example.org"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-a-below-floor-deployment
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-deployment :stake :low
                 :site-id "S-1" :achieved-conformance-level :a
                 :domain "portal.example.org"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-cuts-over-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-production-cutover :stake :high
                 :site-id "S-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))

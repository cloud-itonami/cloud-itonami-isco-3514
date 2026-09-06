(ns webtech.operations-test
  "Regression tests for the two open gates measured at 802ca04:

   1. an op this actor does not offer was approved (:ok? true);
   2. :approve-production-cutover — the riskiest op — skipped every
      site rule, because those rules were gated on
      `(= :approve-deployment op)`.

   Each `hard-on-*` test asserts the SPECIFIC :rule keyword, not merely
   that something was refused: a test that only asserts :hard? passes
   when the refusal came from an unrelated rule, and would have stayed
   green through the very rename it is supposed to catch."
  (:require [clojure.test :refer [deftest is testing]]
            [webtech.store :as store]
            [webtech.governor :as governor]
            [webtech.operations :as operations]
            [webtech.actor :as actor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-site! st {:site-id "S-1" :client-id "client-1"
                              :name "public-portal"
                              :min-conformance-level :aa
                              :approved-domains #{"portal.example.org"}})
    st))

(def ^:private req {:client-id "client-1"})

(defn- rules [v] (set (map :rule (:violations v))))

;; --- the registry itself ----------------------------------------------------

(deftest every-registry-entry-declares-its-obligations
  (testing "an entry missing an obligation key reads as false, which is
            what the permissive branch of defect 2 looked like"
    (is (= {} (operations/underdeclared)))))

(deftest unoffered-ops-are-not-offered
  (is (not (operations/offered? :exfiltrate-customer-database)))
  (is (not (operations/offered? :unknown)))
  (is (not (operations/offered? nil)))
  (is (operations/offered? :approve-deployment))
  (is (operations/offered? :approve-production-cutover)))

;; --- defect 1: an op the actor does not offer --------------------------------

(deftest hard-on-unoffered-op
  (testing "an op nobody declared collects no obligations, so before
            :unoffered-op existed it collected no violations either"
    (let [st (fresh-store)
          v (governor/check req {} {:op :exfiltrate-customer-database
                                    :effect :propose :confidence 0.99} st)]
      (is (:hard? v))
      (is (not (:ok? v)))
      (is (contains? (rules v) :unoffered-op)))))

(deftest hard-on-advisor-emitted-unknown-op
  (testing "webtech.advisor/parse-proposal emits {:op :unknown} for
            unparseable LLM output, so defect 1 was reachable without a
            hostile caller. Low confidence only ESCALATES, and an
            escalation resumes straight into :commit."
    (let [st (fresh-store)
          v (governor/check req {} {:op :unknown :effect :propose
                                    :confidence 0.0 :stake :high} st)]
      (is (:hard? v) "must be HELD, not merely escalated")
      (is (contains? (rules v) :unoffered-op)))))

(deftest unoffered-op-never-reaches-the-store
  (testing "end to end: the graph must commit nothing for an unoffered op"
    (let [st (fresh-store)
          g (actor/build-graph {:store st})]
      (actor/run-request! g (assoc req :op :exfiltrate-customer-database
                                   :site-id "S-1" :stake :low)
                          {} "t-unoffered")
      (is (empty? (store/records-of st "client-1"))
          "no record may be committed for an op the actor does not offer")
      (is (= [:hold] (map :disposition (store/ledger st)))
          "and the hold must be on the audit trail"))))

;; --- defect 2: the riskiest op was the least checked -------------------------

(defn- cutover [extra]
  (merge {:op :approve-production-cutover :effect :propose
          :site-id "S-1" :confidence 0.9 :stake :high}
         extra))

(deftest hard-on-cutover-to-unregistered-site
  (let [st (fresh-store)
        v (governor/check req {} (cutover {:site-id "S-ghost"}) st)]
    (is (:hard? v))
    (is (contains? (rules v) :unknown-site))))

(deftest hard-on-cutover-to-foreign-site
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (cutover {}) st)]
      (is (:hard? v))
      (is (contains? (rules v) :site-wrong-client)))))

(deftest hard-on-cutover-to-unapproved-domain
  (testing "a cutover switches PRODUCTION traffic; domain membership is
            not optional just because a human will sign it"
    (let [st (fresh-store)
          v (governor/check req {} (cutover {:domain "shadow.example.net"
                                             :achieved-conformance-level :aa}) st)]
      (is (:hard? v))
      (is (contains? (rules v) :unapproved-domain)))))

(deftest hard-on-cutover-below-conformance-floor
  (let [st (fresh-store)
        v (governor/check req {} (cutover {:domain "portal.example.org"
                                           :achieved-conformance-level :a}) st)]
    (is (:hard? v))
    (is (contains? (rules v) :conformance-below-floor))))

(deftest conforming-cutover-still-escalates-and-is-never-ok
  (testing "closing the gate must not turn a cutover into an auto-commit"
    (let [st (fresh-store)
          v (governor/check req {} (cutover {:domain "portal.example.org"
                                             :achieved-conformance-level :aa}) st)]
      (is (not (:hard? v)))
      (is (:escalate? v))
      (is (not (:ok? v))))))

(deftest a-held-cutover-is-not-merely-escalated
  (testing "the disposition must be :hold — an escalated run resumes
            straight into :commit, so escalation is not a refusal"
    (let [st (fresh-store)
          g (actor/build-graph {:store st})]
      (actor/run-request! g (assoc req :op :approve-production-cutover
                                   :site-id "S-1" :stake :high
                                   :achieved-conformance-level :a
                                   :domain "portal.example.org")
                          {} "t-cutover-below-floor")
      (is (empty? (store/records-of st "client-1")))
      (is (= [:hold] (map :disposition (store/ledger st)))))))

;; --- the other site-basis ops carry the same obligations ---------------------

(deftest hard-on-maintenance-window-for-unregistered-site
  (testing "every op declaring :requires-site-basis? gets the site rules,
            not just the two that existed when they were written"
    (let [st (fresh-store)
          v (governor/check req {} {:op :schedule-maintenance-window
                                    :effect :propose :site-id "S-ghost"
                                    :confidence 0.9} st)]
      (is (:hard? v))
      (is (contains? (rules v) :unknown-site)))))

(deftest maintenance-window-makes-no-conformance-claim
  (testing "a maintenance window neither claims nor changes a
            conformance level, so the floor does not apply to it"
    (let [st (fresh-store)
          v (governor/check req {} {:op :schedule-maintenance-window
                                    :effect :propose :site-id "S-1"
                                    :confidence 0.9} st)]
      (is (:ok? v))
      (is (empty? (:violations v))))))

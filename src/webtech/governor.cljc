(ns webtech.governor
  "WebTechniciansGovernor — the independent safety/traceability layer
  for the ISCO-08 3514 community web technicians actor (itonami actor
  pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Web-ops twist: WCAG
  conformance is an ORDINAL scale (A < AA < AAA) checked against the
  registered floor — conformance is ordinal, not aspirational — and a
  deployment target domain is either a member of the registered
  approved-domains set or it is not.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. site basis          — a deployment approval must cite a
                           REGISTERED site belonging to this client.
    4. conformance floor   — the proposed achieved-conformance-level
                           must be ordinally >= the site's registered
                           :min-conformance-level (A < AA < AAA;
                           conformance is ordinal, not aspirational).
    5. domain membership   — the proposed deployment domain must be a
                           member of the site's registered
                           :approved-domains set (no unauthorized
                           deployment target).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :approve-production-cutover (DNS/production traffic
                           switch).
    7. low confidence (< `confidence-floor`)."
  (:require [webtech.store :as store]))

(def confidence-floor 0.6)

(def ^:private conformance-rank {:a 1 :aa 2 :aaa 3})

(defn- hard-violations [{:keys [request proposal]} client-record st]
  (let [{:keys [op achieved-conformance-level domain]} proposal
        approve? (= :approve-deployment op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and approve? (nil? st))
      (conj {:rule :unknown-site :detail "未登録 site へのデプロイ承認は不可"})

      (and approve? st (not= (:client-id st) (:client-id request)))
      (conj {:rule :site-wrong-client :detail "site が別 client のもの"})

      (and approve? st achieved-conformance-level
           (< (conformance-rank achieved-conformance-level)
              (conformance-rank (:min-conformance-level st))))
      (conj {:rule :conformance-below-floor
             :detail (str "適合レベル " achieved-conformance-level " < 登録済み下限 "
                          (:min-conformance-level st)
                          "（適合は順序尺度であって願望ではない）")})

      (and approve? st domain (not (contains? (:approved-domains st) domain)))
      (conj {:rule :unapproved-domain
             :detail (str "デプロイ先 " domain " は登録済み承認集合 "
                          (:approved-domains st) " の外")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `webtech.store/Store`. Pure — never mutates the
  store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        st (some->> (:site-id proposal) (store/site store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record st)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :approve-production-cutover (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))

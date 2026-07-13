(ns webtech.advisor
  "WebTechniciansAdvisor — proposes a deployment operation (approve a
  deployment, approve a production cutover) for a registered
  organization. Swappable mock/llm; the advisor ONLY proposes —
  `webtech.governor` checks the conformance floor and domain
  membership independently. Modeled on cloud-itonami-isco-4311's
  advisor.

  A proposal: {:op :approve-deployment|:approve-production-cutover
               :effect :propose :site-id str
               :achieved-conformance-level :a|:aa|:aaa :domain str
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake site-id achieved-conformance-level domain] :as request}]
  {:op op
   :effect :propose
   :site-id site-id
   :achieved-conformance-level achieved-conformance-level
   :domain domain
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a web technician advisor. Given a request, propose an :op,
   the :site-id, :achieved-conformance-level and :domain, an honest
   :confidence and a :stake. Never call a below-floor accessibility
   conformance or an unauthorized deployment domain conforming — the
   governor checks both against the registered site record.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))

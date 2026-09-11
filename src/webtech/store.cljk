(ns webtech.store
  "SSoT for the ISCO-08 3514 community web technicians actor (itonami
  actor pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled
  on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    site   — a registered web site {:site-id :client-id :name
             :min-conformance-level :a|:aa|:aaa
             :approved-domains #{domain-str}}.
             `:min-conformance-level` is the registered WCAG floor a
             proposed deployment's achieved level must meet or exceed
             (ordinal comparison: A < AA < AAA — conformance is
             ordinal, not aspirational); `:approved-domains` is the
             registered set a proposed deployment's target domain
             must be a member of (no unauthorized deployment target).
    record — a committed operating record (approved deployment) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (site [s site-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-site! [s st])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (site [_ site-id] (get-in @a [:sites site-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-site! [s st]
    (swap! a assoc-in [:sites (:site-id st)] st) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :sites {} :records [] :ledger []}
                                   seed)))))

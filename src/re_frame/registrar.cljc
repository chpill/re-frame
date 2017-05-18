(ns re-frame.registrar
  "In many places, re-frame asks you to associate an `id` (keyword)
  with a `handler` (function).  This namespace contains the
  central registry of such associations."
  (:require  [re-frame.interop :refer [debug-enabled?]]
             [re-frame.loggers :refer [console]]))


;; kinds of handlers
(def kinds #{:event :fx :cofx :sub})

;; This atom contains a register of all handlers.
;; Contains a map keyed first by `kind` (of handler), and then `id`.
;; Leaf nodes are handlers.
(def kind->id->handler  (atom {}))

(defn get-handler-from-registry
  ([registry kind]
   (get registry kind))

  ([registry kind id]
   (-> (get registry kind)
       (get id)))

  ([registry kind id required?]
   (let [handler (get-handler-from-registry registry kind id)]
     (when debug-enabled?  ;; This is in a separate when so Closure DCE can run
       (when (and required? (nil? handler)) ;; Otherwise you'd need to type hint the and with a ^boolean for DCE.
         (console :error "re-frame: no " (str kind) " handler registered for:" id)))
     handler)))

(defn get-handler-from-registry-atom [registry-atom & args]
  (apply get-handler-from-registry @registry-atom args))

(defn get-handler [& args]
  (apply get-handler-from-registry @kind->id->handler args))


(defn register-handler-into-registry [registry kind id handler-fn]
  (assoc-in registry [kind id] handler-fn))

(defn register-handler [kind id handler-fn]
  (when debug-enabled?                                       ;; This is in a separate when so Closure DCE can run
    (when (get-handler kind id false)
      (console :warn "re-frame: overwriting" (str kind) "handler for:" id)))   ;; allow it, but warn. Happens on figwheel reloads.

  (swap! kind->id->handler register-handler-into-registry kind id handler-fn)
  handler-fn)    ;; note: returns the just registered handler



(defn clear-handlers-from-registry
  ([registry] {})             ;; clear all kinds

  ([registry kind]        ;; clear all handlers for this kind
   (assert (kinds kind))
   (dissoc registry kind))

  ([registry kind id]     ;; clear a single handler for a kind
   (assert (kinds kind))
   (if (get-handler-from-registry registry kind id)
     (update-in registry [kind] dissoc id)
     (do (console :warn "re-frame: can't clear" (str kind) "handler for" (str id ". Handler not found."))
         registry))))


(defn clear-handlers-from-registry-atom [registry-atom & args]
  (reset! registry-atom
          (apply clear-handlers-from-registry @registry-atom args)))

(defn clear-handlers [& args]
  (reset! kind->id->handler
          (apply clear-handlers-from-registry @kind->id->handler args)))

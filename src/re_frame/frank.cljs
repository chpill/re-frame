(ns re-frame.frank
  (:require [re-frame.utils]
            [re-frame.interop]
            [re-frame.router :as router]
            [re-frame.registrar :as registrar]
            [re-frame.events :as events]

            [re-frame.interceptor :as interceptor]
            [re-frame.loggers :as loggers]
            [re-frame.trace :as trace]))

;;;;;;;;;;
;; TODO ;;
;;;;;;;;;;

;; - create a new way to register an event, that does not use re-frame.cofx/inject-db
;; - profit



;; Adapted from re-frame.registrar/get-handler to take the handler registry
;; value as parameter

(defprotocol Frankenstein
  (dispatch! [this action])
  (dispatch-sync! [this action]))

;; Inspired by scrum reconciler and re-frame internals
(deftype Frank [registry event-queue state-atom]
  Object
  (equiv [this other]
    (-equiv this other))

  IAtom

  IMeta
  (-meta [_] meta)

  IEquiv
  (-equiv [this other]
    (identical? this other))

  IDeref
  (-deref [_]
    (-deref state-atom))

  IWatchable
  (-add-watch [this key callback]
    (add-watch state-atom (list this key)
               (fn [_ _ oldv newv]
                 (when (not= oldv newv)
                   (callback key this oldv newv))))
    this)

  (-remove-watch [this key]
    (remove-watch state-atom (list this key))
    this)

  IHash
  (-hash [this] (goog/getUid this))

  IPrintWithWriter
  (-pr-writer [this writer opts]
    (-write writer "#object [re-frame.frank.Frank ")
    (pr-writer {:val (-deref this)} writer opts)
    (-write writer "]"))


  Frankenstein
  (dispatch! [this event-v]
    (if (nil? event-v)
      (throw (ex-info "re-frankenstein: you called \"dispatch!\" without an event vector." {})))
    (router/push event-queue event-v)

    nil)  ;; Ensure nil return. See https://github.com/Day8/re-frame/wiki/Beware-Returning-False

  (dispatch-sync! [this event-v]
    (events/handle-event-using-registry registry event-v)
    ;; FIXME when we can use an EventQueue
    ;; No post-event-callbacks for now
    #_(-call-post-event-callbacks event-queue event-v)  ;; slightly ugly hack. Run the registered post event callbacks.
    nil)) ;; Ensure nil return

;; TODO as this is used somewhere else, maybe create a `utils` namespace?
(defn- map-vals
  "Returns a new version of 'm' in which 'f' has been applied to each value.
  (map-vals inc {:a 4, :b 2}) => {:a 5, :b 3}"
  [f m]
  (into (empty m)
        (map (fn [[k v]] [k (f v)]))
        m))


(defn swap-stateful-interceptors
  "Modifies the registry value by replacing the handlers that refer refer to the
  global app-db with very similar handlers that refer to the local-db provided
  as argument. Then swap registered stateful interceptors to use new local ones"

  [registry local-db]
  (let [local-db-coeffect-handler
        (fn local-db-coeffect-handler [coeffects]
          (assoc coeffects :db @local-db))

        registry-with-local-db
        (-> registry
            (registrar/register-handler-into-registry :fx
                                                      :db
                                                      (fn [value] (reset! local-db value)))
            (registrar/register-handler-into-registry :cofx
                                                      :db
                                                      local-db-coeffect-handler))

        new-cofx-db-interceptor
        (interceptor/->interceptor
         :id :coeffects/frank-db
         :before (fn coeffects-before [context]
                   (update context
                           :coeffects
                           local-db-coeffect-handler)))

        new-do-fx-interceptor
        (interceptor/->interceptor
         :id :frank/do-fx
         :after (fn do-fx-after [context]
                  (doseq [[effect-k value] (:effects context)]
                    (if-let [effect-fn (registrar/get-handler-from-registry registry-with-local-db
                                                                            :fx
                                                                            effect-k
                                                                            true)]
                      (effect-fn value)))))]

    (update registry-with-local-db :event
            (fn [event-handlers-by-id]
              (map-vals (fn [interceptors]
                          (map (fn [interceptor]
                                 (case (:id interceptor)
                                   :coeffects/db new-cofx-db-interceptor
                                   :do-fx new-do-fx-interceptor
                                   interceptor))
                               interceptors
                               ))
                        event-handlers-by-id)))))

(defn create []
  (let [local-db (atom {})
        global-registry @registrar/kind->id->handler
        scoped-registry (swap-stateful-interceptors global-registry
                                                    local-db)]

    (->Frank scoped-registry
             (router/->EventQueue (delay scoped-registry)
                                  :idle     ;; Initial queue state
                                  #queue [] ;; Internal storage for actions
                                  {})       ;; Function to be called after every action
             local-db)))

(ns re-frame.frank
  (:require [re-frame.utils]
            [re-frame.interop :as interop]
            [re-frame.router :as router]
            [re-frame.registrar :as registrar]
            [re-frame.events :as events]

            [re-frame.interceptor :as interceptor]
            [re-frame.loggers :as loggers]
            [re-frame.trace :as trace]))


;; Adapted from re-frame.registrar/get-handler to take the handler registry
;; value as parameter

(defprotocol Frankenstein
  (dispatch! [this event-v])
  (dispatch-sync! [this event-v]))

;; Inspired by scrum reconciler and re-frame internals
(deftype Frank [registry-atom event-queue state-atom]
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
    (events/handle-event-using-registry @registry-atom event-v)
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


(defn swap-stateful-interceptors!
  "Modifies the registry value by replacing the handlers that refer refer to the
  global app-db with very similar handlers that refer to the local-db provided
  as argument. Then swap registered stateful interceptors to use new local ones"

  [registry-atom local-db frank]
  (let [local-db-coeffect-handler
        (fn local-db-coeffect-handler [coeffects]
          (assoc coeffects :db @local-db))

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
                    (if-let [effect-fn
                             (registrar/get-handler-from-registry-atom registry-atom
                                                                       :fx
                                                                       effect-k
                                                                       true)]
                      (effect-fn value
                                 {:dispatch!      #(dispatch! frank %)
                                  :dispatch-sync! #(dispatch-sync! frank %)})))))]

    (reset! registry-atom
            (-> @registry-atom
                (registrar/register-handler-into-registry :cofx
                                                          :db
                                                          local-db-coeffect-handler)

                (registrar/register-handler-into-registry :fx
                                                          :db
                                                          (fn [value] (reset! local-db value)))

                (registrar/register-handler-into-registry
                 :fx :dispatch-later
                 (fn [value {local-dispatch :dispatch!}]
                   (doseq [{:keys [ms dispatch] :as effect} value]
                     (if (or (empty? dispatch) (not (number? ms)))
                       (loggers/console :error "re-frame: ignoring bad :dispatch-later value:" effect)
                       (interop/set-timeout! #(local-dispatch dispatch) ms)))))

                (registrar/register-handler-into-registry
                 :fx :dispatch
                 (fn [value {local-dispatch :dispatch!}]
                   (if-not (vector? value)
                     (loggers/console :error "re-frame: ignoring bad :dispatch value. Expected a vector, but got:" value)
                     (local-dispatch value))))

                (registrar/register-handler-into-registry
                 :fx :dispatch-n
                 (fn [value {local-dispatch :dispatch!}]
                   (if-not (sequential? value)
                     (loggers/console :error "re-frame: ignoring bad :dispatch-n value. Expected a collection, got got:" value)
                     (doseq [event value] (local-dispatch event)))))


                (registrar/register-handler-into-registry
                 :fx :deregister-event-handler
                 (fn [value]
                   (let [clear-event (partial registrar/clear-handlers-from-registry-atom registry-atom :fx)]
                     (doseq [event (if (sequential? value) value [value])]
                       (clear-event event)))))

                (update :event
                        (fn [event-handlers-by-id]
                          (map-vals (fn [interceptors]
                                      (map (fn [interceptor]
                                             (case (:id interceptor)
                                               :coeffects/db new-cofx-db-interceptor
                                               :do-fx new-do-fx-interceptor
                                               interceptor))
                                           interceptors))
                                    event-handlers-by-id)))))))

(defn create []
  (let [local-db (atom {})
        local-registry-atom (atom @registrar/kind->id->handler)
        frank (->Frank local-registry-atom
                       (router/->EventQueue local-registry-atom
                                            :idle     ;; Initial queue state
                                            #queue [] ;; Internal storage for actions
                                            {})       ;; Function to be called after every action
                       local-db)]

    (swap-stateful-interceptors! local-registry-atom
                                 local-db
                                 frank)

    frank))

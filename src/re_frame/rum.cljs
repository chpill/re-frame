(ns re-frame.rum
  (:require [goog.object :as gobj]
            [re-frame.frank :as frank]))


(defn wrap-dispatch [dispatch-fn frank-instance]
  (fn [event args]
    (dispatch-fn frank-instance event args)))

;; Surprisingly, trying to require react in the let just beside failed silently...
(def ^:private React (cond (exists? js/React) js/React
                           (exists? js/require) (js/require "react")
                           :else (throw "Did you forget to include react?")))

(def ^:private PropTypes (cond (exists? js/PropTypes) js/PropTypes
                               (exists js/React.PropTypes) js/React.PropTypes
                               (exists? js/require) (js/require "prop-types")
                               :else (throw "Did you forget to include prop-types?")))

(let [frank-k "re-frame/frank"
      context-types {frank-k PropTypes.object}]

  (defn inject-frank-into-context
    "Inject Frank instance into current react context. The Frank instance
  must be provided as argument to the component to which this mixins is applied.
  The `get-frank-fn` will then be used to extract the Frank from the
  arguments."
    [get-frank-fn]
    {:init (fn [rum-state _]
             (assoc rum-state ::frank (get-frank-fn (:rum/args rum-state))))
     :class-properties {:childContextTypes context-types}

     ;; Pending issue https://github.com/tonsky/rum/pull/137
     :child-context-types context-types

     :child-context (fn [rum-state] {frank-k (::frank rum-state)})})

  (def with-frank
    {:class-properties {:contextTypes context-types}

     ;; Pending issue https://github.com/tonsky/rum/pull/137
     :context-types context-types

     :will-mount (fn [rum-state]
                   (let [frank (-> (:rum/react-component rum-state)
                                   (gobj/get "context")
                                   (gobj/get frank-k))]
                     (assoc rum-state
                            :frank/frank frank
                            :frank/dispatch! (wrap-dispatch frank/dispatch! frank)
                            :frank/dispatch-sync! (wrap-dispatch frank/dispatch-sync! frank))))
     :will-unmount (fn [rum-state] (dissoc rum-state
                                           :frank/frank
                                           :frank/dispatch!
                                           :frank/dispatch-sync!))})

  ;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; TEMPORARY WORKAROUND ;;
  ;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; Use these mixins if you want to use both `re-frame.frank`
  ;; `org org.martinklepsch.derivatives`.

  ;; This is to make code a bit more concise to declare react contextTypes and
  ;; childContextTypes. Hopefully rum will soon improve its handling of react
  ;; context types.

  (let [aggregated-context-types (assoc context-types
                             "org.martinklepsch.derivatives/get"     PropTypes.func
                             "org.martinklepsch.derivatives/release" PropTypes.func)]

    (def drv+frank-ctx {:class-properties {:contextTypes aggregated-context-types}})
    (def drv+frank-child-ctx {:class-properties {:childContextTypes aggregated-context-types}})))




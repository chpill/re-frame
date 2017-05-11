(ns todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [secretary.core :as secretary]
            [todomvc.events]
            [todomvc.subs]
            [todomvc.views]
            [devtools.core :as devtools]

            [todomvc.frank-subs]
            [todomvc.frank-views]
            [rum.core :as rum]
            [re-frame.frank :as frank]
            [re-frame.rum :as re-rum]
            [org.martinklepsch.derivatives :as derivatives]
            )
  (:import [goog History]
           [goog.history EventType]))


;; -- Debugging aids ----------------------------------------------------------
(devtools/install!)       ;; we love https://github.com/binaryage/cljs-devtools
(enable-console-print!)   ;; so that println writes to `console.log`



;; Root component for our rum views
;; Inject all we need into the context

(rum/defc rum-root
  < (re-rum/inject-frank-into-context first)
    (derivatives/rum-derivatives* second)
    re-rum/drv+frank-child-ctx
  [frank derivative-specs]
  (todomvc.frank-views/todo-app))

(defonce current-frank-todos (atom []))

(defn re-mount-all []
  ;; Clean up previous mounted instances (when hot loading code)
  (doseq [node (-> (js/document.getElementsByClassName "frank-todos")
                   array-seq)]
    (.remove node))

  (doseq [frank @current-frank-todos]
    (let [add-frank-button (js/document.getElementById "more-frank-todos")
          container (doto (js/document.createElement "div")
                      (.. -classList (add "frank-todos")))

          derivative-specs (todomvc.frank-subs/make-derivative-specs frank)]

      (.insertBefore document.body container add-frank-button)

      (rum/mount (rum-root frank
                           derivative-specs)
                 container))))

(defn add-frank-todos []
  (let [frank (frank/create)]
    (frank/dispatch-sync! frank [:initialise-db])
    ;; add dispatcher to the routing
    (swap! current-frank-todos conj frank))

  (re-mount-all))

(defonce only-on-initial-load
  (do (add-frank-todos)
      (doto (js/document.getElementById "more-frank-todos")
        (.addEventListener "click" add-frank-todos))))



;; -- Entry Point -------------------------------------------------------------
;; Within ../../resources/public/index.html you'll see this code
;;    window.onload = function () {
;;      todomvc.core.main();
;;    }
;; So this is the entry function that kicks off the app once the HTML is loaded.
;;
(defn ^:export main
  []
  ;; Put an initial value into app-db.
  ;; The event handler for `:initialise-db` can be found in `events.cljs`
  ;; Using the sync version of dispatch means that value is in
  ;; place before we go onto the next step.
  (dispatch-sync [:initialise-db])

  ;; Render the UI into the HTML's <div id="app" /> element
  ;; The view function `todomvc.views/todo-app` is the
  ;; root view for the entire UI.
  (reagent/render [todomvc.views/todo-app]    ;;
                  (.getElementById js/document "app"))


  ;; force a re-mounting of every frank todos
  (re-mount-all))

;; -- Routes and History ------------------------------------------------------
;; Although we use the secretary library below, that's mostly a historical
;; accident. You might also consider using:
;;   - https://github.com/DomKM/silk
;;   - https://github.com/juxt/bidi
;; We don't have a strong opinion.


(defn dispatch-all [event-v]
  ;; Traditional re-frame dispatch for the traditional re-frame todos
  (dispatch event-v)
  ;; Dispatch to every frank we currently have
  (doseq [frank @current-frank-todos]
    (frank/dispatch! frank event-v)))

(defroute "/" [] (dispatch-all [:set-showing :all]))
(defroute "/:filter" [filter] (dispatch-all [:set-showing (keyword filter)]))

(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))


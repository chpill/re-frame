(ns simple.core
  (:require [reagent.core :as reagent]
            [rum.core :as rum]
            [re-frame.rum :as re-rum]
            [re-frame.frank :as frank]
            [org.martinklepsch.derivatives :as derivatives]
            [re-frame.core :as rf]))

;; A detailed walk-through of this source code is provied in the docs:
;; https://github.com/Day8/re-frame/blob/master/docs/CodeWalkthrough.md

;; -- Domino 1 - Event Dispatch -----------------------------------------------

(defn dispatch-timer-event
  []
  (let [now (js/Date.)]
    (rf/dispatch [:timer now])))  ;; <-- dispatch used

;; Call the dispatching function every second.
;; `defonce` is like `def` but it ensures only instance is ever
;; created in the face of figwheel hot-reloading of this file.
(defonce do-timer (js/setInterval dispatch-timer-event 1000))


;; -- Domino 1 bis - See the end of this file!


;; -- Domino 2 - Event Handlers -----------------------------------------------

(rf/reg-event-db              ;; sets up initial application state
  :initialize                 ;; usage:  (dispatch [:initialize])
  (fn [_ _]                   ;; the two parameters are not important here, so use _
    {:time (js/Date.)         ;; What it returns becomes the new application state
     :time-color "#f88"}))    ;; so the application state will initially be a map with two keys


(rf/reg-event-db                ;; usage:  (dispatch [:time-color-change 34562])
  :time-color-change            ;; dispatched when the user enters a new colour into the UI text field
  (fn [db [_ new-color-value]]  ;; -db event handlers given 2 parameters:  current application state and event (a vector)
    (assoc db :time-color new-color-value)))   ;; compute and return the new application state


(rf/reg-event-db                 ;; usage:  (dispatch [:timer a-js-Date])
  :timer                         ;; every second an event of this kind will be dispatched
  (fn [db [_ new-time]]          ;; note how the 2nd parameter is destructured to obtain the data value
    (assoc db :time new-time)))  ;; compute and return the new application state


;; Note that there is no need for a Domino 2 bis!


;; -- Domino 4 - Query  -------------------------------------------------------

(rf/reg-sub
  :time
  (fn [db _]     ;; db is current app state. 2nd unused param is query vector
    (-> db
        :time)))

(rf/reg-sub
  :time-color
  (fn [db _]
    (:time-color db)))


;; -- Domino 4 bis - Derivatives  -------------------------------------------------------

(defn make-derivative-specs [base]
  {:base       [[] base]
   :time       [[:base] :time]
   :time-color [[:base] :time-color]})


;; -- Domino 5 - View Functions ----------------------------------------------

(defn clock
  []
  [:div.example-clock
   {:style {:color @(rf/subscribe [:time-color])}}
   (-> @(rf/subscribe [:time])
       .toTimeString
       (clojure.string/split " ")
       first)])

(defn color-input
  []
  [:div.color-input
   "Time color: "
   [:input {:type "text"
            :value @(rf/subscribe [:time-color])
            :on-change #(rf/dispatch [:time-color-change (-> % .-target .-value)])}]])  ;; <---

(defn ui
  []
  [:div
   [:h1 "Hello world, it is now"]
   [clock]
   [color-input]])


;; -- Domino 5 bis - Rum View Functions ----------------------------------------------
;; Note that there are no globals here. The dispatch functions and the
;; subscription to derived values go through the react context.

(rum/defcs rum-clock
  < rum/reactive
    (derivatives/drv :time :time-color)
  [rum-state]
  [:div.example-clock
   {:style {:color (derivatives/react rum-state :time-color)}}
   (-> (derivatives/react rum-state :time)
       .toTimeString
       (clojure.string/split " ")
       first)])

(rum/defcs rum-color-input
  < rum/reactive
    (derivatives/drv :time-color)
    re-rum/with-frank
    re-rum/drv+frank-ctx

  [{dispatch! :frank/dispatch! :as rum-state}]
  [:div.color-input
   "Time color: "
   [:input {:type "text"
            :value (derivatives/react rum-state :time-color)
            :on-change #(dispatch! [:time-color-change (-> % .-target .-value)])}]])

(rum/defc rum-ui []
  [:div
   [:h1 "Hello Victor, it is now"]
   (rum-clock)
   (rum-color-input)])

(rum/defc rum-root
  "This component does not render anything by itself, it just calls another
view. It does however take inputs and injects them into the local react context"
  < (re-rum/inject-frank-into-context first)
    (derivatives/rum-derivatives* second)
    re-rum/drv+frank-child-ctx
  [frank derivative-specs]
  (rum-ui))


;; -- Entry Point -------------------------------------------------------------

(defn ^:export run
  []
  ;; Mount the original app
  (rf/dispatch-sync [:initialize])     ;; puts a value into application state
  (reagent/render [ui]              ;; mount the application's ui into '<div id="app" />'
                  (js/document.getElementById "app"))

  ;; Create and mount a new app using `re-frame.frank`
  (let [frank (frank/create)]
    (frank/dispatch-sync! frank [:initialize])
    (frank/dispatch-sync! frank [:time-color-change "#8d8"])

    ;; Domino 1 bis must be set up here, because we need the frank instance to dispatch
    (let [scoped-dispatch-timer-event
          (fn [] (let [now (js/Date.)]
                   ;; We are going to add an hour to make very explicit we do
                   ;; not display the global state
                   (frank/dispatch! frank [:timer now])))]

      ;; DO NOT PUT `defonce` here.
      ;; You need to to it with vanilla re-frame because there is a `defonce app-db`.
      ;; Here we re-create the frank instance on every reload of the file so no problem.
      (js/setInterval scoped-dispatch-timer-event 1000))

    (rum/mount (rum-root frank
                         (make-derivative-specs frank))
               (js/document.getElementById "frankenstein"))))


(ns re-frame.rum-test
  (:require [cljs.test :refer-macros [is deftest use-fixtures]]
            [re-frame.core :as re-frame]
            [re-frame.db]
            [re-frame.registrar :as registrar]
            [re-frame.frank :as frank]
            [re-frame.rum :as re-rum]
            [rum.core :as rum]
            [cljsjs.react.dom.server]))

;; ---- FIXTURES ---------------------------------------------------------------

;; This fixture uses the re-frame.core/make-restore-fn to checkpoint and reset
;; to cleanup any dynamically registered handlers from our tests.
(defn fixture-re-frame
  []
  (let [restore-re-frame (atom nil)]
    {:before (fn []
               (reset! restore-re-frame (re-frame.core/make-restore-fn))
               (reset! re-frame.db/app-db {})
               (registrar/clear-handlers))
     :after  #(@restore-re-frame)}))


(use-fixtures :each (fixture-re-frame))

;; ---- Rum components ---------------------------------------------------------

(defn extract-counter [rum-state]
  (-> rum-state
      :frank/frank
      deref
      :counter))

(rum/defcs leaf-view
  < re-rum/with-frank
  [rum-state]
  [:h1 (str "Counter: " (extract-counter rum-state))])

(rum/defc intermediary-view []
  [:div (leaf-view)])

(rum/defc root-view
  < (re-rum/inject-frank-into-context first)
  [frank]
  (intermediary-view))

;; ---- TESTS ------------------------------------------------------------------

(deftest inject-and-access-react-context
  (re-frame/reg-event-db ::init
                         (fn [db [_]]
                           (assoc db :counter 0)))

  (let [frank (frank/create)]
    (frank/dispatch-sync! frank [::init])

    (is (= "<div><h1>Counter: 0</h1></div>"
           (js/ReactDOMServer.renderToStaticMarkup (root-view frank))))))

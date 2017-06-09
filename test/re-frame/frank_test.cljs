(ns re-frame.frank-test
  (:require [cljs.test :refer-macros [is deftest async use-fixtures testing]]
            [re-frame.core :as re-frame]
            [re-frame.db]
            [re-frame.std-interceptors :as std-interceptors]
            [re-frame.registrar :as registrar]
            [re-frame.frank :as frank]))

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


;; ---- TESTS ------------------------------------------------------------------

(deftest empty-frank
  (testing "create works"
    (is (frank/create)))

  (testing "default value is an empty map"
    (let [frank (frank/create)]
      (is (= {} @frank)))))

(deftest synchronous
  (testing "re-frame.db/app-db is empty"
    (is (= {} @re-frame.db/app-db)))
  ;; check that we can write in the db
  (re-frame/reg-event-db ::insert-plop (fn [db [_ message]] (assoc db :plop message)))
  ;;(is (frank/create))
  (testing "dispatch and mutate only our new monster"
    (let [frank (frank/create)]
      (frank/dispatch-sync! frank [::insert-plop "plop"])
      (is (= {:plop "plop"} @frank))
      (is (= {} @re-frame.db/app-db))))

  ;; check that we can read what we wrote in the db
  (re-frame/reg-event-db
   ::plop-after-plop
   (fn [db [_ plop]] (update db :plop clojure.string/upper-case)))


  (testing "we can use the mutated local state in further transactions "
    (let [frank (frank/create)]
     (frank/dispatch-sync! frank [::insert-plop "plop"])
     (frank/dispatch-sync! frank [::plop-after-plop])
     (is (= {:plop "PLOP"} @frank)))))

(deftest asynchronous
  (re-frame/reg-event-db ::insert-plop (fn [db [_ message]] (assoc db :plop message)))
  (re-frame/reg-event-db
   ::plop-after-plop
   (fn [db [_ plop]] (update db :plop clojure.string/upper-case)))

  (testing "using the internal eventQueue"
    (let [frank (frank/create)]
      (frank/dispatch! frank [::insert-plop "plop"])
      (frank/dispatch! frank [::plop-after-plop])

      (async done
             (js/setTimeout (fn []
                              (is (= {:plop "PLOP"} @frank))
                              (done))
                            100)))))


(deftest with-interceptors
  (re-frame/reg-event-db ::insert-plop
                         [std-interceptors/trim-v
                          (std-interceptors/path :plop)]
                         (fn [db [message]] message))

  (testing "interceptors work as usual"
    (let [frank (frank/create)]
      (frank/dispatch-sync! frank [::insert-plop "plouf"])
      (is (= {:plop "plouf"} @frank)))))

(deftest with-standard-effects
  (re-frame/reg-event-fx ::trigger-some-standard-effects
                         (fn [_ _]
                           {:db {:plop 0}
                            :dispatch [::inc]
                            :dispatch-later [{:ms 5 :dispatch [::inc]}
                                             {:ms 5 :dispatch [::inc]}]
                            :dispatch-n [[::inc]
                                         [::inc]]}))
  (re-frame/reg-event-db ::inc
                         [(std-interceptors/path :plop)]
                         (fn [val _] (inc val)))

  (let [frank (frank/create)]
    (frank/dispatch-sync! frank [::trigger-some-standard-effects])
    (is (= {:plop 0} @frank))

    (async done
           (js/setTimeout (fn []
                            (is (= {:plop 5} @frank))
                            (done))
                          10))))

(deftest with-custom-effect
  (re-frame/reg-event-fx ::trigger-side-plop
                         (fn [_ _]
                           {:db {:plop "step 1"}
                            ::side-plop {:some-key "step 2"}}))
  (re-frame/reg-fx ::side-plop
                   (fn [{value :some-key} {:keys [dispatch!]}]
                     (dispatch! [::side-plop-success value])))
  (re-frame/reg-event-db ::side-plop-success
                         [(std-interceptors/path :plop)
                          std-interceptors/trim-v]
                         (fn [_ [value]] value))
  (let [frank (frank/create)]
    (frank/dispatch-sync! frank [::trigger-side-plop])
    (is (= {:plop "step 1"} @frank))

    (async done
           (js/setTimeout (fn []
                            (is (= {:plop "step 2"} @frank))
                            (done))
                          100))))


(deftest with-starting-atom
  (re-frame/reg-event-db ::inc
                         [(std-interceptors/path :plop)]
                         (fn [val _] (inc val)))
  (let [starting-atom (atom {:plop 1})
        frank (frank/create starting-atom)]
    (frank/dispatch-sync! frank [::inc])
    (is (= {:plop 2} @frank))))

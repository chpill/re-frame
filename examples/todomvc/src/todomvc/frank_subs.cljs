(ns todomvc.frank-subs)

(defn sorted-todos [base]
  (:todos base))

(defn make-derivative-specs [base]
  {:base          [[] base]
   :showing       [[:base] :showing]
   :sorted-todos  [[:base] sorted-todos]
   :todos         [[:sorted-todos] vals]

   :visible-todos [[:todos :showing]
                   (fn [todos showing]
                     (let [filter-fn (case showing
                                       :active (complement :done)
                                       :done   :done
                                       :all    identity)]
                       (filter filter-fn todos)))]

   ;; The name of this derivation (and the original subscription which inspired
   ;; it) is misleading.
   :all-complete?   [[:todos] #(seq %)]
   :completed-count [[:todos] #(count (filter :done %))]

   :footer-counts   [[:todos :completed-count]
                     (fn [todos completed]
                       [(- (count todos) completed) completed])]})

(ns todomvc.frank-views
  (:require [rum.core :as rum]
            [re-frame.rum :as re-rum]
            [org.martinklepsch.derivatives :as d]))


(rum/defcs todo-input
  < (rum/local "TODO this should come from the props" ::title)
  {:will-mount (fn [{title ::title :as rum-state}]
                 (reset! title
                         (-> rum-state :rum/args first :title))
                 rum-state)}
  [{val ::title :as rum-state}
   {:keys [title on-save on-stop] :as props}]
  (let [stop #(do (reset! val "")
                  (when on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (when (seq v) (on-save v))
                (stop))]
    [:input (merge (dissoc props :on-save :on-stop)
                   {:type "text"
                    :value @val
                    :auto-focus true
                    :on-blur save
                    :on-change #(reset! val (-> % .-target .-value))
                    :on-key-down #(case (.-which %)
                                    13 (save)
                                    27 (stop)
                                    nil)})]))

(rum/defcs todo-item
  < re-rum/with-frank
    (rum/local false ::editing)
    {:key-fn (fn [{:keys [id]}] id)}
  [{dispatch! :frank/dispatch!
    editing   ::editing}
   {:keys [id done title]}]
  [:li {:class (str (when done "completed ")
                    (when @editing "editing"))}
   [:div.view
    [:input.toggle
     {:type "checkbox"
      :checked done
      :on-change #(dispatch! [:toggle-done id])}]
    [:label
     {:on-double-click #(reset! editing true)}
     title]
    [:button.destroy
     {:on-click #(dispatch! [:delete-todo id])}]]
   (when @editing
     (todo-input {:class "edit"
                  :title title
                  :on-save #(dispatch! [:save id %])
                  :on-stop #(reset! editing false)}))])



(rum/defcs task-list
  < re-rum/with-frank
    rum/reactive
    (d/drv :visible-todos :all-complete?)
    re-rum/drv+frank-ctx
  [{dispatch! :frank/dispatch! :as rum-state}]
  (let [visible-todos (d/react rum-state :visible-todos)
        all-complete? (d/react rum-state :all-complete?)]
    [:section.main
     [:input.toggle-all
      {:type "checkbox"
       :checked all-complete?
       :on-change #(dispatch! [:complete-all-toggle (not all-complete?)])}]
     [:label
      {:for "toggle-all"}
      "Mark all as complete"]
     [:ul.todo-list
      (for [todo  visible-todos]
        (todo-item todo))]]))


(rum/defcs footer-controls
  < re-rum/with-frank
    rum/reactive
    (d/drv :footer-counts :showing)
    re-rum/drv+frank-ctx
  [{dispatch! :frank/dispatch! :as rum-state}]
  (let [[active done] (d/react rum-state :footer-counts)
        showing       (d/react rum-state :showing)
        a-fn          (fn [filter-kw txt]
                        [:a {:class (when (= filter-kw showing) "selected")
                             :href (str "#/" (name filter-kw))} txt])]
    [:footer.footer
     [:span.todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul.filters
      [:li (a-fn :all    "All")]
      [:li (a-fn :active "Active")]
      [:li (a-fn :done   "Completed")]]
     (when (pos? done)
       [:button.clear-completed {:on-click #(dispatch! [:clear-completed])}
        "Clear completed"])]))

(rum/defcs task-entry
  < re-rum/with-frank
  [{dispatch! :frank/dispatch!}]
  [:header.header
   [:h1 {:style {:color "#8d8"}} "todos"]
   (todo-input {:class "new-todo"
                :placeholder "What needs to be done?"
                :on-save #(dispatch! [:add-todo %])})])

(rum/defcs todo-app
  < rum/reactive
    (d/drv :todos)
  [rum-state]
  [:div
   [:section.todoapp
    (task-entry)
    (when (seq (d/react rum-state :todos))
      (task-list))
    (footer-controls)]
   [:footer.info
    [:p "Double-click to edit a todo"]]])

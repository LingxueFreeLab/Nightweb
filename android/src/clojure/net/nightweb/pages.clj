(ns net.nightweb.pages
  (:use [neko.resource :only [get-resource get-string]]
        [neko.activity :only [set-content-view!]]
        [net.nightweb.clandroid.activity :only [defactivity]]
        [net.nightweb.clandroid.service :only [bind-service unbind-service]]
        [net.nightweb.views :only [create-tab
                                   get-grid-view
                                   get-user-view
                                   get-category-view]]
        [net.nightweb.menus :only [create-main-menu]]
        [net.nightweb.actions :only [do-menu-action]]
        [nightweb.router :only [user-hash-bytes]]
        [nightweb.db :only [defspec
                            run-query
                            drop-tables
                            create-tables
                            insert-test-data]]))

(defn start-service
  ([context] (start-service context (fn [binder])))
  ([context on-connected]
   (let [service (bind-service context "net.nightweb.MainService" on-connected)]
     (swap! (.state context) assoc :service service))))

(defn stop-service
  [context]
  (if-let [service (get @(.state context) :service)]
    (unbind-service context service)))

(defn start-receiver
  [context]
  (let [receiver (proxy [android.content.BroadcastReceiver] []
                   (onReceive [context intent]
                     (.finish context)))]
    (.registerReceiver context
                       receiver
                       (android.content.IntentFilter. "ACTION_CLOSE_APP"))
    (swap! (.state context) assoc :receiver receiver)))

(defn stop-receiver
  [context]
  (if-let [receiver (get @(.state context) :receiver)]
    (.unregisterReceiver context receiver)))

(defn set-share-content
  [context content]
  (swap! (.state context) assoc :share-content content))

(defactivity
  net.nightweb.MainPage
  :on-create
  (fn [this bundle]
    (defspec (.getAbsolutePath (.getFilesDir this)) nil)
    (run-query drop-tables nil nil)
    (run-query create-tables nil nil)
    ;(run-query insert-test-data nil nil)
    (start-service
      this
      (fn [binder]
        (let [action-bar (.getActionBar this)]
          (.setNavigationMode action-bar android.app.ActionBar/NAVIGATION_MODE_TABS)
          (.setDisplayShowTitleEnabled action-bar false)
          (.setDisplayShowHomeEnabled action-bar false)
          (create-tab action-bar
                      (get-string :me)
                      #(let [content {:type :users
                                      :hash user-hash-bytes}]
                         (set-share-content this content)
                         (get-user-view this content)))
          (create-tab action-bar
                      (get-string :users)
                      #(let [content {:type :users}]
                         (set-share-content this content)
                         (get-category-view this content true)))
          (create-tab action-bar
                      (get-string :posts)
                      #(let [content {:type :posts}]
                         (set-share-content this content)
                         (get-category-view this content true))))))
    (start-receiver this))
  :on-destroy
  (fn [this]
    (stop-receiver this)
    (stop-service this))
  :on-create-options-menu
  (fn [this menu]
    (create-main-menu this menu true)))

(defactivity
  net.nightweb.FavoritesPage
  :on-create
  (fn [this bundle]
    ; start service and receiver
    (start-service this)
    (start-receiver this)
    ; create ui
    (let [params (into {} (.getSerializableExtra (.getIntent this) "params"))
          action-bar (.getActionBar this)]
      (.setNavigationMode action-bar android.app.ActionBar/NAVIGATION_MODE_TABS)
      (.setDisplayHomeAsUpEnabled action-bar true)
      (.setTitle action-bar (get-string :favorites))
      (create-tab action-bar
                  (get-string :users)
                  #(get-category-view this
                                      (assoc params :type :users-favorites)))
      (create-tab action-bar
                  (get-string :posts)
                  #(get-category-view this
                                      (assoc params :type :posts-favorites)))))
  :on-destroy
  (fn [this]
    (stop-receiver this)
    (stop-service this))
  :on-create-options-menu
  (fn [this menu]
    (create-main-menu this menu false))
  :on-options-item-selected
  (fn [this item]
    (do-menu-action this item)))

(defactivity
  net.nightweb.TransfersPage
  :on-create
  (fn [this bundle]
    ; start service and receiver
    (start-service this)
    (start-receiver this)
    ; create ui
    (let [action-bar (.getActionBar this)]
      (.setNavigationMode action-bar android.app.ActionBar/NAVIGATION_MODE_TABS)
      (.setDisplayHomeAsUpEnabled action-bar true)
      (.setTitle action-bar (get-string :transfers))
      (create-tab action-bar
                  (get-string :all)
                  #(get-category-view this {:type :all-transfers}))
      (create-tab action-bar
                  (get-string :photos)
                  #(get-category-view this {:type :photos-transfers}))
      (create-tab action-bar
                  (get-string :videos)
                  #(get-category-view this {:type :videos-transfers}))
      (create-tab action-bar
                  (get-string :audio)
                  #(get-category-view this {:type :audio-transfers}))))
  :on-destroy
  (fn [this]
    (stop-receiver this)
    (stop-service this))
  :on-create-options-menu
  (fn [this menu]
    (create-main-menu this menu false))
  :on-options-item-selected
  (fn [this item]
    (do-menu-action this item)))

(defactivity
  net.nightweb.GridPage
  :def grid-page
  :on-create
  (fn [this bundle]
    ; start service and receiver
    (start-service this)
    (start-receiver this)
    ; create ui
    (let [params (.getSerializableExtra (.getIntent this) "params")
          action-bar (.getActionBar this)
          grid-view (case (get params :type)
                      :users (get-user-view this params)
                      (get-grid-view this []))]
      (set-share-content this params)
      (.setDisplayHomeAsUpEnabled action-bar true)
      (if-let [title (get params :text)]
        (.setTitle action-bar title)
        (.setDisplayShowTitleEnabled action-bar false))
      (set-content-view! grid-page grid-view)))
  :on-destroy
  (fn [this]
    (stop-receiver this)
    (stop-service this))
  :on-create-options-menu
  (fn [this menu]
    (create-main-menu this menu true))
  :on-options-item-selected
  (fn [this item]
    (do-menu-action this item)))

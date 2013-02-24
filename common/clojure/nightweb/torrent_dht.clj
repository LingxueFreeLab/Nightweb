(ns nightweb.torrent-dht
  (:use [clojure.java.io :only [file]]
        [nightweb.torrent :only [manager
                                 iterate-torrents
                                 iterate-peers
                                 add-hash
                                 remove-torrent]]
        [nightweb.io :only [write-file
                            base32-encode
                            b-encode
                            b-decode
                            b-decode-bytes
                            b-decode-number
                            read-priv-node-key-file
                            read-pub-node-key-file
                            write-priv-node-key-file
                            write-pub-node-key-file
                            read-key-file
                            read-link-file]]
        [nightweb.constants :only [users-dir
                                   torrent-ext
                                   get-user-dir
                                   get-user-pub-file
                                   get-meta-dir
                                   get-meta-link-file]]
        [nightweb.crypto :only [verify-signature]]))

(defn get-node-info-for-peer
  [peer]
  (let [dht (.getDHT (.util manager))
        destination (.getAddress (.getPeerID peer))]
    (.getNodeInfo dht destination)))

(defn get-public-node
  []
  (if-let [dht (.getDHT (.util manager))]
    (.toPersistentString (.getNodeInfo dht nil))
    (println "Failed to get public node")))

(defn get-private-node
  []
  (if-let [socket-manager (.getSocketManager (.util manager))]
    (.getSession socket-manager)
    (println "Failed to get private node")))

(defn add-node
  [node-info-str]
  (if-let [dht (.getDHT (.util manager))]
    (.heardAbout dht (org.klomp.snark.dht.NodeInfo. node-info-str))
    (println "Failed to add bootstrap node")))

(defn save-meta-link
  [link-map]
  (let [user-hash-str (get link-map :user-hash-str)
        link-path (get-meta-link-file user-hash-str)]
    (write-file link-path (get link-map :link))))

(defn send-custom-query
  [node-info method args]
  (let [query (doto (java.util.HashMap.)
                (.put "q" method)
                (.put "a" args))]
    (.sendQuery (.getDHT (.util manager)) node-info query true)))

(defn send-meta-link
  [torrent]
  (if (.getPersistent torrent)
    (iterate-peers torrent
                   (fn [peer]
                     (let [node-info (get-node-info-for-peer peer)
                           info-hash-str (base32-encode (.getInfoHash torrent))
                           args (read-link-file info-hash-str)]
                       (println "Sending meta link" node-info args)
                       (send-custom-query node-info "announce_meta" args))))))

(defn send-meta-link-periodically
  [seconds]
  (future
    (while true
      (java.lang.Thread/sleep (* seconds 1000))
      (iterate-torrents send-meta-link))))

(defn parse-meta-link
  [link]
  (let [{data-val "data" sig-val "sig"} link
        data-map (b-decode (b-decode-bytes data-val))
        {user-hash-val "user_hash"
         link-hash-val "link_hash"
         time-val "time"} data-map
        user-hash-bytes (b-decode-bytes user-hash-val)
        link-hash-bytes (b-decode-bytes link-hash-val)
        time-num (b-decode-number time-val)]
    (if (and link data-val user-hash-bytes)
      {:link (b-encode link)
       :data (b-decode-bytes data-val)
       :sig (b-decode-bytes sig-val)
       :user-hash-str (base32-encode user-hash-bytes)
       :link-hash-str (base32-encode link-hash-bytes)
       :time time-num})))

(defn validate-meta-link
  [link-map]
  (and link-map
       (get link-map :time)
       (<= (get link-map :time) (.getTime (java.util.Date.)))
       (let [user-hash-str (get link-map :user-hash-str)
             pub-key-path (get-user-pub-file user-hash-str)]
         (verify-signature (read-key-file pub-key-path)
                           (get link-map :sig)
                           (get link-map :data)))))

(defn replace-meta-link
  [user-hash-str old-link-map new-link-map]
  (let [user-dir (get-user-dir user-hash-str)
        meta-file (str (get-meta-dir user-hash-str) torrent-ext)]
    (save-meta-link new-link-map)
    (remove-torrent meta-file)
    (remove-torrent (get old-link-map :link-hash-str))
    (add-hash user-dir (get new-link-map :link-hash-str) nil)
    (println "Saved meta link")))

(defn compare-meta-link
  [link-map]
  (let [user-hash-str (get link-map :user-hash-str)
        my-link (read-link-file user-hash-str)
        my-link-map (parse-meta-link my-link)
        my-time (get my-link-map :time)
        their-time (get link-map :time)]
    (if (not= my-time their-time)
      (if (validate-meta-link link-map)
        (if (or (nil? my-time) (> their-time my-time))
          (replace-meta-link user-hash-str my-link-map link-map)
          my-link)
        my-link)
      (println "Received identical link" my-time their-time))))

(defn receive-meta-link
  [args]
  (println "Received meta link" args)
  (if-let [link (parse-meta-link args)]
    (compare-meta-link link)
    (println "Meta link can't be parsed")))

(defn init-dht
  []
  ; set the node keys from the disk
  (let [priv-node (read-priv-node-key-file)
        pub-node (read-pub-node-key-file)]
    (if (and priv-node pub-node)
      (.setDHTNode (.util manager) priv-node pub-node)))
  ; set the custom query handler
  (.setDHTCustomQueryHandler
    (.util manager)
    (reify org.klomp.snark.dht.CustomQueryHandler
      (receiveQuery [this method args]
        (case method
          "announce_meta" (receive-meta-link args)))
      (receiveResponse [this args]
        (receive-meta-link args))))
  ; set the init callback
  (.setDHTInitCallback
    (.util manager)
    (fn []
      (let [priv-node (get-private-node)
            pub-node (get-public-node)]
        (when (and priv-node pub-node)
          (write-priv-node-key-file priv-node)
          (write-pub-node-key-file pub-node)))
      (add-node "rkJ0ws6PQz8FU7VvTW~Lelhb6DM=:rkJ0wkX6jrW3HJBNdhuLlWCUPKDAlX8T23lrTOeMGK8=:B5QFqHHlCT5fOA2QWLAlAKba1hIjW-KBt2HCqwtJg8JFa2KnjAzcexyveYT8HOcMB~W6nhwhzQ7~sywFkvcvRkKHbf6LqP0X43q9y2ADFk2t9LpUle-L-x34ZodEEDxQbwWo74f-rX5IemW2-Du-8NH-o124OGvq5N4uT4PjtxmgSVrBYVLjZRYFUWgdmgR1lVOncfMDbXzXGf~HdY97s9ZFHYyi7ymwzlk4bBN9-Pd4I1tJB2sYBzk62s3gzY1TlDKOdy7qy1Eyr4SEISAopJrvAnSkS1eIFyCoqfzzrBWM11uWppWetf3AkHxGitJIQe73wmZrrO36jHNewIct54v2iF~~3cqBVlT4ptX1Dc-thjrxXoV73A0HUASldCeFZSVJFMQgOQK9U85NQscAokftpyp4Ai89YWaUvSDcZPd-mQuA275zifPwp8s8UfYV5EBqvdHnfeJjxmyTcKR3g5Ft8ABai9yywxoA7yoABD4EGzsFtAh0nOLcmbM944zdAAAA:35701")
      (println "DHT initialized"))))

(ns nightweb.constants)

(def base-dir nil)
(defn set-base-dir
  [dir]
  (def base-dir dir))

(def my-hash-bytes nil)
(defn set-my-hash-bytes
  [hash-bytes]
  (def my-hash-bytes hash-bytes))

(def my-hash-str nil)
(defn set-my-hash-str
  [hash-str]
  (def my-hash-str hash-str))

(def slash java.io.File/separator)
(def nw-dir (str slash "nwapp"))
(def meta-dir (str slash "meta"))
(def posts-dir (str slash "posts"))
(def files-dir (str slash "files"))

(def priv-key "private.key")
(def pub-key "public.key")

(def torrent-ext ".torrent")
(def post-ext ".post")
(def link-ext ".link")

(def priv-key-file (str nw-dir slash priv-key))
(def pub-key-file (str nw-dir slash pub-key))
(def priv-node-key-file (str nw-dir slash "private.node.key"))
(def pub-node-key-file (str nw-dir slash "public.node.key"))
(def profile-file (str nw-dir slash "user.profile"))
(def db-file (str nw-dir slash "main"))
(def users-dir (str nw-dir slash "users"))

(defn get-users-dir
  []
  (str base-dir users-dir))

(defn get-user-dir
  [user-hash]
  (str base-dir users-dir slash user-hash))

(defn get-user-pub-file
  [user-hash]
  (str (get-user-dir user-hash) slash pub-key))

(defn get-meta-dir
  [user-hash]
  (str (get-user-dir user-hash) meta-dir))

(defn get-meta-link-file
  [user-hash]
  (str (get-meta-dir user-hash) link-ext))

(defn get-posts-dir
  [user-hash]
  (str (get-meta-dir user-hash) posts-dir))

(defn get-files-dir
  [user-hash]
  (str (get-meta-dir user-hash) files-dir))

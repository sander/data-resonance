(ns wifi.arduino
  (:require [serial.core :as serial]
            [clojure.core.async :as async :refer [<! put! chan timeout go-loop go close! alts! sliding-buffer]]))

(def address "tty.usbmodem1421")
(def recv-keys [:pressure-l :pressure-r
                :motor-l :motor-r
                :attached-l :attached-r])

(defn- parse-value [msg]
  (->> (-> msg (.substring 1) (.split ","))
       (map read-string)
       (zipmap recv-keys)))

(defn- process [send values state msg]
  (case (first msg)
    \? (do (println "establishing")
           (put! send "!\n")
           state)
    \! (do (println "established")
           (assoc state :established true))
    \V (do (put! values (parse-value msg))
           state)
    (do (println "unknown message:" (str "[" msg "]"))
        state)))

(defn- has-bytes? [in] (> (.available in) 0))
(defn- equals [v] #(= % v))
(defn- nequals [v] #(not= % v))

(def messagify (comp (map char)
                     (partition-by (equals \newline))
                     (map #(apply str %))
                     (map clojure.string/trim)
                     (filter (nequals ""))))
(comment (into [] messagify (slurp "test.txt")))

(defn- listener-to-chan [ch stream]
  (while (has-bytes? stream)
    (put! ch (.read stream))))

(extend-protocol serial.core/Bytable
  String
  (to-bytes [this] (.getBytes this "ASCII")))

(defn- send-message [state port msg]
  (serial/write port msg)
  state)

(defn connect [in out stop]
  (let [port (serial/open address)
        from (chan 2 messagify)]
    (serial/listen port (partial listener-to-chan from) false)
    (go-loop [state {}]
      (let [[val ch] (alts! [from in stop])]
        (condp = ch
          from (recur (process in out state val))
          in (recur (send-message state port val))
          stop (serial/close port))))))

(def in (chan))
(def out (chan (sliding-buffer 1)))
(def stop (chan))
(connect in out stop)

(defn set-motors [l r]
  (put! in (str l \, r \newline)))

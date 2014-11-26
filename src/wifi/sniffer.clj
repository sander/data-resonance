(ns wifi.sniffer
  (:require [clojure.core.async :refer [chan go go-loop <! >! sub] :as async]
            [clojure.core.async.lab :refer [spool] :as async.lab]
            [wifi.process :as process]
            [clojure.string :refer [split]]
            [clojure.data.priority-map :refer [priority-map]]))

(def fields {:frame.number read-string
             ;:ip.addr
             :wlan.sa identity
             :wlan.da identity
             :wlan.fc.type #(let [v (read-string %)]
                             (case v
                               0 :management
                               1 :control
                               2 :data
                               v))
             :wlan.fc.subtype #(let [v (read-string %)]
                                (case v
                                  0 :association-request
                                  1 :association-response
                                  4 :probe-request
                                  5 :probe-response
                                  8 :beacon
                                  11 :authentication
                                  12 :deauthentication
                                  v))
             :data.len read-string
             :data.data identity})
; right click in wireshark, apply as filter > selected

(def cmd (vec (concat ["tshark" "-Tfields" "-Il"] (flatten (map #(vec ["-e" (name %)]) (keys fields))))))

(defn combine-with-fields [v] (zipmap (keys fields) (split v #"\t")))
(defn process-fields [v] (reduce-kv (fn [m k v] (assoc m k ((k fields) v))) {} v))
(defn process-output [lines] (sequence (comp (map combine-with-fields) (map process-fields)) lines))
(def topics [:data :other])
(defn topic-fn [p] (cond (= (:wlan.fc.type p) :data) :data :else :other))

(defn sniff [] (process/start cmd process-output topic-fn))
(def stop process/stop)

(defn counter [ch]
  "also empties the chan"
  (let [result (atom 0)]
    (go-loop []
      (when (<! ch)
        (swap! result inc)
        (recur)))
    result))
;(pipe (chan 2 (map (constantly 1))))

(defn last-item [ch]
  "also empties the chan"
  (let [res (atom nil)]
    (go (while (reset! res (<! ch))))
    res))

(defn all [sn]
  (let [res (chan)]
    (doseq [t topics] (sub (:pub sn) t res))
    res))

(defn data
  ([sniffer] (data sniffer (chan)))
  ([sniffer s] (sub (:pub sniffer) :data s) s))

(defn increase-priority-item [item map]
  (swap! map update-in [item] (if (contains? @map item) inc (constantly 1))))
(defn top-addresses [sniffer]
  (let [addresses (atom (priority-map))
        packets (data sniffer)]
    (go-loop []
      (when-let [p (async/<! packets)]
        (increase-priority-item (:wlan.sa p) addresses)
        (increase-priority-item (:wlan.da p) addresses)
        (recur)))
    addresses))
(defn addresses>str [addr]
  (apply str (flatten (for [[addr count] (take 5 (rseq addr))] [" - " addr ": " count "\n"]))))

(defn size-accumulator [ch]
  (let [out (atom 0)]
    (go-loop []
      (when-let [v (<! ch)]
        (swap! out + (if-let [len (:data.len v)] len 0))
        (recur)))
    out))

(defn accumulator [ch]
  (let [out (atom 0)]
    (go-loop []
      (when-let [v (<! ch)]
        (swap! out + (if-let [len (:data.len v)] len 0))
        (recur)))
    out))

(defn accumulate [f init ch]
  (let [out (chan)]
    (go-loop [result init]
      (>! out result)
      (when-let [v (<! ch)]
        (recur (f result v))))
    out))
(defn last-value [ch]
  (let [res (atom nil)]
    (go (while (reset! res (<! ch))))
    res))

(comment
  ;; own sounds
  (def skip 50)
  (def own-count (atom 0))
  (def ch6 (async-lab/spool only-data))
  (def self "24:e3:14:26:72:4e")
  (def self "28:94:0f:aa:97:5f")
  (defn own-loop []
    (let [p (async/<!! ch6)]
      (if (or (= (:wlan.sa p) self) (= (:wlan.da p) self))
        (do
          (swap! own-count inc)
          (if (> @own-count skip)
            (do
              (tink-sample)
              (reset! own-count 0)))))))
  (async/go-loop []
                 (own-loop)
                 (recur))

  (def show-lines 20)
  (def lines-buffer (async/sliding-buffer show-lines))
  (def lines-chan (async/chan lines-buffer))
  (def ch7 (async-lab/spool only-data))
  (defn slide-loop []
    (let [p (async/<!! ch7)]
      (if-not (nil? p)
        (if-not (nil? (:data.data p))
          (async/put! lines-chan (:data.data p))))))
  (async/go-loop []
                 (slide-loop)
                 (recur))
  )
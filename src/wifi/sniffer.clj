(ns wifi.sniffer
  (:require [clojure.core.async :as async]
            [clojure.core.async.lab :as async.lab]))

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

(def cmd (vec
           (concat ["tshark" "-Tfields" "-Il"]
                   (flatten (map (fn [f]
                                   ["-e" (name f)])
                                 (keys fields))))))

(defn combine-with-fields [v]
  (zipmap (keys fields) (clojure.string/split v #"\t")))
(defn process-fields [v]
  (reduce-kv (fn [m k v] (assoc m k ((k fields) v))) {} v))
(defn process-output [lines]
  (sequence (comp
              (map combine-with-fields)
              (map process-fields))
            lines))
(def topics [:data :other])
(defn topic-fn [p]
  (cond (= (:wlan.fc.type p) :data) :data
        :else :other))

(defn sniff []
  (let [process (.start (ProcessBuilder. cmd))
        stdout (.getInputStream process)
        reader (clojure.java.io/reader stdout)
        lines (line-seq reader)
        processed (process-output lines)
        ch (async.lab/spool processed)
        publication (async/pub ch topic-fn)]
    ;; “Items received when there are no matching subs get dropped.”
    {:pub publication
     :stop (fn []
             (.destroy process)
             (async/close! ch))}))
(defn stop [sn] ((:stop sn)))

(defn counter [ch]
  "also empties the chan"
  (let [result (atom 0)]
    (async/go-loop []
                   (when (async/<! ch)
                     (swap! result inc)
                     (recur)))
    result))

(defn last-item [ch]
  "also empties the chan"
  (let [res (atom nil)]
    (async/go (while (reset! res (async/<! ch))))
    res))

(defn all [sn]
  (let [res (async/chan)]
    (doseq [t topics]
      (async/sub (:pub sn) t res))
    res))

(comment
  (def sn (sniff))
  (def su (async/chan))
  (async/sub (:pub sn) :data su)
  (def cnt (counter su))

  ;; stopping
  (async/unsub (:pub sn) :data su)
  (stop sn)

  ;; old stuff
  (def addresses (atom (priority-map)))
  (def ch3 (async-lab/spool only-data))
  (defn update-address [addr]
    (if-not (contains? @addresses addr)
      (swap! addresses assoc addr 0))
    (swap! addresses update-in [addr] inc))
  (async/go-loop []
                 (let [p (async/<! ch3)]
                   (update-address (:wlan.sa p))
                   (update-address (:wlan.da p))
                   (recur)))
  (println (take 5 (rseq @addresses)))


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
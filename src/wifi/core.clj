(ns wifi.core
  (:require [quil.core :as q]
            [overtone.core :as overtone]
            [clojure.core.async :as async]
            [clojure.core.async.lab :as async-lab]
            [clojure.data.priority-map :refer [priority-map]])
  (:gen-class))

(defonce sc-server (overtone/boot-server))
(def pop-sample (overtone/sample "/System/Library/Sounds/Pop.aiff"))
(def tink-sample (overtone/sample "/System/Library/Sounds/Tink.aiff"))

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
              :data.len read-string})
; right click in wireshark, apply as filter > selected

(def cmd ["tshark" "-Il" "-s96"])
(def cmd (vec (concat ["tshark" "-Tfields" "-Il"] (flatten (map (fn [f] ["-e" (name f)]) (keys fields))))))

(defn sniff []
  (let [process (.start (ProcessBuilder. cmd))
        stdout (.getInputStream process)
        reader (clojure.java.io/reader stdout)
        lines (line-seq reader)
        ;ch (async-lab/spool lines)
        ]
    [lines
     (fn []
       (.destroy process)
       ;(async/close! ch)
       )]))

(defn processed [result]
  (sequence (comp
              (map (fn [v] (zipmap (keys fields) (clojure.string/split v #"\t"))))
              (map (fn [v] (reduce-kv (fn [m k v] (assoc m k ((k fields) v))) {} v)))) (first result)))

(def result (sniff))
(def proc (processed result))
(def only-data
  (filter #(= (:wlan.fc.type %) :data) proc))
;((second result))
(for [line (take 20 proc)]
  (println line))
(defn close [] ((second result)))

(def counter (atom 0))

(def ch (async-lab/spool only-data))
(def lastp (atom nil))

;(async/close! ch)
(async/go-loop []
               (let [p (async/<! ch)]
                 (if p
                   (do
                     (swap! counter inc)
                     (reset! lastp p)
                     ;(pop-sample)
                     (recur)))))

(def total (atom 0))
(def ch2 (async-lab/spool proc))
(async/go-loop []
               (let [p (async/<! ch2)]
                 (if p
                   (do
                     (swap! total inc)
                     (recur)))))

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

;; counter
(def interval 1000)
(def ival-count (atom 0))
(def delay (atom 1000))
(def delay-based-on (atom 0))
(def factor 50)
(def ch5 (async-lab/spool only-data))
(async/go-loop []
               (let [p (async/<! ch5)]
                 (swap! ival-count inc)
                 (recur)))
(async/go-loop []
               (async/<! (async/timeout interval))
               (reset! delay (if (> @ival-count 0)
                               (int (* factor (/ interval (double @ival-count))))
                               interval))
               (reset! delay-based-on @ival-count)
               (reset! ival-count 0)
               (recur))

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

;;delay is too big

;; play sounds
(async/go-loop []
               (if (= @delay-based-on 0)
                 nil
                 (pop-sample))
               (async/<! (async/timeout @delay))
               (recur))

;; may be interesting to differentiate signals sent by the most present MAC address (probably the router)

(defn sketch []
  (defn setup []
    (q/smooth)
    (q/frame-rate 60)
    (q/background 200))
  (defn draw []
    ;(q/stroke (q/random 255) 0 0)
    ;(q/stroke-weight (q/random 10))
    ;(q/fill 0 0 (q/random 255))
    (q/background 0)
    (q/text (str @counter "/" @total " (" @own-count ")") 0 30)
    (q/text (str @lastp) 0 60)
    (q/text (reduce
              str
              (map
                (fn [[addr count]]
                  (str addr ": " count "\n"))
                (take 5 (rseq @addresses))))
            0 90)

    (q/fill 255)
    (q/ellipse (/ (q/width) 2) (/ (q/height) 2) 50)

    (let [diam (q/random 100)
          ;      x (q/random (q/width))
          ;y (q/random (q/height))
          ]
      ;(q/ellipse x y diam diam)
      ))

  (q/defsketch wifi-sketch
               :title "Wi-Fi"
               :setup setup
               :draw draw
               :size [800 200]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "hello world"))
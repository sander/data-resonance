(ns wifi.core
  (:require [quil.core :as q]
            ;[overtone.core :as overtone]
            [clojure.core.async :as async]
            [clojure.core.async.lab :as async-lab])
  (:gen-class))

;(defonce sc-server (overtone/boot-server))
;(def pop-sample (overtone/sample "/System/Library/Sounds/Pop.aiff"))
;(def tink-sample (overtone/sample "/System/Library/Sounds/Tink.aiff"))

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
                                  5 :prober-response
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
  (->> (first result)
       (sequence (map (fn [v]
                        (zipmap (keys fields) (clojure.string/split v #"\t")))))
       (sequence (map (fn [v]
                        (reduce-kv (fn [m k v]
                                     (assoc m k ((k fields) v))) {} v))))))

(def result (sniff))
(def proc (processed result))
(def only-data
  (filter #(= (:wlan.fc.type %) :data) proc))
;((second result))
(for [line (take 20 proc)]
  (println line))

(def counter (atom 0))

(def ch (async-lab/spool only-data))
;(async/close! ch)
(async/go-loop []
               (async/<! ch)
               (swap! counter inc)
               ;(pop-sample)
               (recur))

(def total (atom 0))
(def lastp (atom nil))
(def ch (async-lab/spool proc))
(async/go-loop []
               (reset! lastp (async/<! ch))
               (swap! total inc)
               (recur))

(defn sketch []
  (defn setup []
    (q/smooth)
    (q/frame-rate 60)
    (q/background 200))
  (defn draw []
    ;(q/stroke (q/random 255) 0 0)
    ;(q/stroke-weight (q/random 10))
    ;(q/fill 0 0 (q/random 255))
    (q/background 200)
    (q/text (str "test" "\n" @counter) 0 100)

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
               :size [323 200]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "hello world"))
(ns wifi.core
  (:require [quil.core :as q]
            [overtone.core :as overtone]
            [clojure.core.async :as async]
            [clojure.core.async.lab :as async-lab])
  (:gen-class))

(defn start-overtone []
  (defonce sc-server (overtone/boot-server))
  (def pop-sample (overtone/sample "/System/Library/Sounds/Pop.aiff"))
  (def tink-sample (overtone/sample "/System/Library/Sounds/Tink.aiff")))

(defn sniff []
  (let [process (.start (ProcessBuilder. ["tshark" "-Il" "-s96"]))
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
(def regex #"^\s*(\d+)\s+(\d+)\.(\d+)\s+([^\s]+)\s+([^\s]+) -> ([^\s]+)\s+([^\s]+)\s+([^\s]+)\s+(.+)$")
(defn split [result]
  (sequence (map (fn [v]
                   (zipmap '(:seq :s :us :from :from-role :to :to-role :protocol :data)
                           (rest (re-find regex v))))) (first result)))
(defn parse-line
  ([result type [ffirst & nrest :as items]]
   (case type
     :seq (assoc (parse-line result :time nrest) :seq (read-string ffirst) :original items)
     :time (assoc (parse-line result :from nrest) :time (read-string ffirst))
     :from (if (= ffirst "->")
             (parse-line result :to nrest)
             (if-not (= (first nrest) "->")
               (assoc (parse-line result :to (rest (rest nrest))) :from ffirst :from-type (first nrest))
               (assoc (parse-line result :to (rest nrest)) :from ffirst)))
     :to (if-not (= (first nrest) "->")
           (assoc (parse-line result :protocol (rest nrest)) :to ffirst :to-type (first nrest))
           (assoc (parse-line result :protocol nrest) :to ffirst))
     :protocol (assoc (parse-line result :data nrest) :protocol ffirst)
     result)))
(defn easier-split [result]
  (sequence (map (fn [v]
                   (parse-line {} :seq (-> v
                                           (clojure.string/trim)
                                           (clojure.string/split #"\s+"))))) (first result)))
(def res (sniff))
(def es (easier-split res))
(for [line (take 100 es)] (println line))

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
(ns wifi.animation
  (:require [clojure.core.async :as async
             :refer [chan go >! <! timeout go-loop alts! close!]]
            [wifi.haptic :as hap]))

(def fps 60)

(defn millis
  "Returns the current time in ms."
  []
  (System/currentTimeMillis))

(defn interval
  "Returns a channel that has true avaialble every t ms."
  [t]
  (let [ch (chan)]
    (go (while (>! ch true) (<! (timeout t))))
    ch))

(defn t
  "Returns the current time as a float between start (0.0) and end (1.0)."
  [start end]
  (float (if (= end 0)
           0
           (max 0 (min 1 (/ (- (millis) start)
                            (- end start)))))))

(defn- handle-new-state
  [[state duration]]
  (let [start (millis)
        end (if duration (+ start duration) 0)]
    [state start end]))
(defn- handle-tick
  [state start end]
  (if (< (millis) end)
    (do
      (if (fn? state) (state (t start end)))
      [state start end])
    [nil 0 0]))

(defn animator
  "Calls the state function fps times per second."
  []
  (let [new-state (chan)
        ticks (interval (/ 1000 fps))
        stop (chan)
        ports [new-state ticks stop]]
    (go-loop
      [[state start end] [nil 0 0]]
      (let [[msg c] (alts! ports)]
        (if (= c stop)
          (doseq [c ports] (close! c))
          (recur
            (cond
              (= c new-state) (handle-new-state msg)
              (= c ticks) (handle-tick state start end))))))
    {:new-state new-state
     :stop stop}))

(defn set-state
  "Sets the state for an animator for dur ms."
  [anim st dur]
  (go (>! (:new-state anim) [st dur])))

(defn stop
  "Stops an animator."
  [anim]
  (go (>! (:stop anim) true)))

(defn lerp
  [v w t]
  (+ (* (- 1 t) v)
     (* t w)))
(defn rlerp
  [& args]
  (Math/round (apply lerp args)))

(comment
  (def motor (animator))
  (require '[wifi.haptic :as hap])
  (set-state motor #(println %) 100)
  (defn move [p1 p2]
    (let [[s1 s2] @hap/last-motor-status]
      (set-state motor #(hap/set-motors
                         (rlerp s1 p1 %)
                         (rlerp s2 p2 %)) 500)))
  (move 9 5))
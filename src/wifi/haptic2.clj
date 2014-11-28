(ns wifi.haptic2
  (:require [serial.core :as serial]
            [clojure.core.async :as async :refer [<! timeout go]]))

(defn connect [in out stop]
  (let [state (atom {:running true})
        port (serial/open "tty.usbmodem1411")]
    (serial/listen port (fn [in]
                          (async/put! out (.read in))) false)
    (async/go
      (while (@state :running)
          (serial/write port (byte-array (async/<! in)))))
    (async/go
      (async/<! stop)
      (swap! state assoc :running false)
      (serial/remove-listener port)
      (serial/close port))))

(defn indexes-of [e coll] (keep-indexed #(if (= e %2) %1) coll))
(defn next-in [e cycle] (nth cycle (inc (first (indexes-of e cycle)))))
(defn create-sensor-channel [] (async/chan (async/sliding-buffer 1)))

(defn listen [out stop]
  (let [running (atom true)
        vals [:servo1 :servo2 :pressure]
        order (cycle vals)
        current (atom (first vals))
        output (atom (zipmap vals (take (count vals) (repeat -1))))]
    (async/go
      (async/<! stop)
      (reset! running false))
    (async/go
      (while @running
        (let [b (async/<! out)]
          (if (= b 0)
            (reset! current (first vals))
            (do
              (if-not (= (@output @current) b)
                (swap! output assoc @current b))
              (swap! current next-in order))))))
    output))

(def in (async/chan))
(def out (async/chan                                        ; (async/sliding-buffer 1)
           ))
(def stop (async/chan))
(connect in out stop)
(def listen-stop (async/chan))
(def values (listen out listen-stop))

(defn adjust-motors [m1 m2]
  (async/put! in [255 (+ 127 m1) (+ 127 m2)]))

(def vibrating (atom false))
(def vibration-intensity 8)
(def vibration-up (atom false))
(def fps 15)
(defn vibrate []
  (async/go-loop []
    (when @vibrating
      (let [add (if @vibration-up vibration-intensity 0)]
        (apply adjust-motors (take 2 (repeat add))))
      (swap! vibration-up not)
      (async/<! (async/timeout (/ 1000 fps)))
      (recur))))
(defn set-vibrating! [v]
  (when (not= @vibrating v)
    (reset! vibrating v)
    (if v (vibrate) (adjust-motors 0 0))))
(defn vibrate-once! []
  (async/go
    (when (not @vibrating)
      (reset! vibrating true)
      (apply adjust-motors (take 2 (repeat vibration-intensity)))
      (async/<! (async/timeout (/ 1000 fps)))
      (adjust-motors 0 0)
      (reset! vibrating false))))

(defn bounce! [depth offset duration wait]
  (go
    (when (not @vibrating)
      (let [up offset
            down (+ depth offset)]
        (reset! vibrating true)
        (adjust-motors down down)
        (<! (timeout duration))
        (adjust-motors up up)
        (<! (timeout wait))
        (reset! vibrating false)))))

(comment
  (go
    (adjust-motors 16 16)
    (<! (timeout 50))
    (adjust-motors 10 10)
    (<! (timeout 50))
    (adjust-motors 4 4)
    (<! (timeout 50))
    (adjust-motors 0 0)))

(defn dbg-stop []
  (async/put! stop true)
  (async/put! listen-stop true)
  (async/close! out))

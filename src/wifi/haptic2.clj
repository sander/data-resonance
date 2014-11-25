(ns wifi.haptic2
  (:require [serial.core :as serial]
            [clojure.core.async :as async]))

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

(defn dbg-stop []
  (async/put! stop true)
  (async/put! listen-stop true)
  (async/close! out))

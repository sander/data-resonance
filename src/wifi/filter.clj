(ns wifi.filter
  (:require [clojure.core.async :as async :refer [go timeout alts!]]))

(def stabilize-threshold 3)
(def stabilize-timeout 200)

(defn millis
  "Returns the current time in ms."
  []
  (System/currentTimeMillis))

(defn below-threshold?
  [v w thres]
  (< (Math/abs (- v w)) thres))

(defn handle-value
  [val last-time at at-stable]
  (if (below-threshold? val @at stabilize-threshold)
    (if (> (- (millis) @last-time) stabilize-timeout)
      (if (not (below-threshold? @at-stable val stabilize-threshold))
        (reset! at-stable val)))
    (reset! last-time (millis)))
  (reset! at val))

(defn stabilize
  [at at-stable ch]
  (let [last-time (atom 0)]
    (go (while
          (let [timeout (timeout stabilize-timeout)
                [val port] (alts! [ch timeout])]
            (condp = port
              ch (handle-value val last-time at at-stable)
              timeout (handle-value @at last-time at at-stable)))))))
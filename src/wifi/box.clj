(ns wifi.box
  (:require [wifi.interaction :as ix]
            [wifi.sniffer :as sn]
            [clojure.core.async :as async]
            [wifi.haptic :as hap]))

(def running (atom true))

(def sniff (sn/sniff))
(def total (sn/counter (sn/all sniff)))

(let [ch (async/chan)]
  (async/sub (:pub sniff) :data ch)
  (def total-data (sn/counter ch)))

(def lastp (sn/last-item (sn/all sniff)))

(def interval 1000)
(def sound-delay (atom interval))
(def sound-delay-based-on (atom 0))
(def sound-delay-factor 100)

(def only-data (async/chan))
(async/sub (:pub sniff) :data only-data)
(def ival-count (sn/counter only-data))
(defn sound-loop []
  (async/<!! (async/timeout interval))
  (reset! sound-delay (if (> @ival-count 0)
                        (int (* sound-delay-factor
                                (/ interval (double @ival-count))))
                        interval))
  (reset! sound-delay-based-on @ival-count)
  (reset! ival-count 0))
(async/go (while @running
            (sound-loop)))

(defn sound-play-loop []
  (when (not= @sound-delay-based-on 0)
    (comment
      (reset! color 0)
      (if with-arduino
        (firmata/set-digital board 2 :high))
      (if (> @sound-delay color-duration)
        (async/go
          (async/<! (async/timeout color-duration))
          (reset! color 255)
          (if with-arduino
            (firmata/set-digital board 2 :low)))))
    (println "vibrate")
    (ix/vibrate 60 4 1))
  (async/<!! (async/timeout @sound-delay)))
(async/go (while @running
            (sound-play-loop)))

(defn stop []
  (reset! running false)
  (async/unsub-all (:pub sniff))
  (hap/close)
  (sn/stop sniff))

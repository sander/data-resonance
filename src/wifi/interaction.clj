(ns wifi.interaction
  (:require [wifi.haptic :as hap]
            [wifi.animation :as ani]
            [clojure.core.async :refer [chan put! <! go-loop go alts! >! <!! timeout sliding-buffer]]))

;(def flat [40 30])
;(def sunken [50 60])

(def flat [7 12])
(def sunken [128 128])

(def motor (ani/animator))

(defn lerp
  [v w t]
  (+ (* (- 1 t) v)
     (* t w)))
(defn rlerp
  [& args]
  (Math/round (apply lerp args)))

(defn vibrate
  ([] (vibrate 100))
  ([dur] (vibrate dur 1))
  ([dur int] (vibrate dur int 1))
  ([dur int each]
   (let [[v10 v20] @hap/last-motor-status
         v11 v10
         v12 (+ v11 int)
         v21 v20
         v22 (+ v21 int)
         st1 (atom true)
         step (atom 0)]
     (ani/set-state motor
                    (fn [t]
                      (if (= 0 (swap! step #(rem (inc %) each)))
                        (apply hap/set-motors (if (swap! st1 not)
                                                [v11 v21]
                                                [v12 v22]))))
                    dur))))

(defn move
  [[p1 p2] dur]
  (hap/disable-vibration! dur)
  (let [[s1 s2] @hap/last-motor-status]
    (ani/set-state motor
                   #(hap/set-motors
                     (rlerp s1 p1 %)
                     (rlerp s2 p2 %))
                   dur)))

(comment
  (move flat 300)
  (move sunken 100))

;; debounce from https://github.com/swannodette/async-tests/blob/master/src/async_test/utils/helpers.cljs
;; Copyright © 2013 David Nolen
;; Distributed under the Eclipse Public License, the same as Clojure.
(defn debounce
  ([source msecs]
   (debounce (chan) source msecs))
  ([c source msecs]
   (go
     (loop [state ::init cs [source]]
       (let [[_ threshold] cs]
         (let [[v sc] (alts! cs)]
           (condp = sc
             source (condp = state
                      ::init
                      (do (>! c v)
                          (recur ::debouncing
                                 (conj cs (timeout msecs))))
                      ::debouncing
                      (recur state
                             (conj (pop cs) (timeout msecs))))
             threshold (recur ::init (pop cs)))))))
   c))

(defn semidebounce
  [source msecs]
  (let [c (chan (sliding-buffer 1))]
    (go-loop [state ::init
              [_ threshold :as cs] [source]
              next nil]
             (let [[v sc] (alts! cs)]
               (condp = sc
                 source (condp = state
                          ::init
                          (do (>! c v)
                              (recur ::debouncing (conj cs (timeout msecs)) nil))
                          ::debouncing
                          (recur state (conj (pop cs) (timeout msecs)) v))
                 threshold (if next
                             (do (>! c next)
                                 (recur ::debouncing (conj (pop cs) (timeout msecs)) nil))
                             (recur ::init (pop cs) nil)))))
    c))

;; second attempt
(defn semidebounce
  [source msecs]
  (let [c (chan (sliding-buffer 1))]
    (go-loop [state ::init
              [_ threshold :as cs] [source]
              next nil]
             (let [[v sc] (alts! cs)]
               (condp = sc
                 source (condp = state
                          ::init
                          (do (>! c v)
                              (recur ::debouncing (conj cs (timeout msecs)) nil))
                          ::debouncing
                          (recur state (conj (pop cs) (timeout msecs)) v))
                 threshold (if next
                             (do (>! c next)
                                 (recur ::debouncing (conj (pop cs) (timeout msecs)) nil))
                             (recur ::init (pop cs) nil)))))
    c))

;; nah try dnolen's
(defn semidebounce
  [source msecs]
  (debounce source msecs))

(defonce ix-instance (atom nil))
(defn ix
  ([]
   (if @ix-instance
     (@ix-instance))
   (let [[touch dist] (hap/listen2)
         li (semidebounce touch 100)
         st (chan)]
     (ix li st)
     (reset! ix-instance
             (fn []
               (put! st true)
               (hap/unlisten)))))
  ([touch stop]
   (go-loop []
            (let [[msg c] (alts! [touch stop])]
              (println "msg" msg)
              (condp = [msg c]
                [:release touch] (move flat 300)
                [:press touch] (move sunken 100)
                nil)
              (if (not= c stop) (recur))))))
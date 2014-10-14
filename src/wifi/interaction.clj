(ns wifi.interaction
  (:require [wifi.haptic :as hap]
            [wifi.animation :as ani]))

(def flat [40 30])
(def sunken [50 60])

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
  (let [[s1 s2] @hap/last-motor-status]
    (ani/set-state motor
                   #(hap/set-motors
                     (rlerp s1 p1 %)
                     (rlerp s2 p2 %))
                   dur)))

(comment
  (move flat 300)
  (move sunken 100))
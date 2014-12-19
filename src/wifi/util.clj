(ns wifi.util
  (:require [clojure.core.async :refer [go go-loop chan <! >! <!! timeout close! pipe alts! sliding-buffer] :as async]))

(defn millis
  "Returns the current time in ms."
  []
  (System/currentTimeMillis))

(defn transduce-chan [xf ch] (async/pipe ch (async/chan 2 xf)))

(defn reduce-over-interval [f init]
  (letfn [(g [in ival out]
             (go-loop []
               (let [collect (pipe in (chan))
                     val (async/reduce f init collect)]
                 (<! (timeout ival))
                 (close! collect)
                 (>! out (<! val))
                 (recur))))]
    (fn
      ([in ival] (let [out (chan (sliding-buffer 1))] (g in ival out) out))
      ([in ival out] (g in ival out)))))

(def sum-values (reduce-over-interval + 0))

(defn mapchan [f ch] (->> f map (chan 2) (pipe ch)))
(defn map-to-ones [ch] (->> 1 constantly map (chan 2) (pipe ch)))

(defn count-values [in & args] (apply (reduce-over-interval + 0) (map-to-ones in) args))

(defn last-item [ch]
  (let [res (atom nil)]
    (go
      (while (reset! res (<! ch)))
      (reset! res nil))
    res))

(defn interpolate
  ([value ival]
    (fn [now last-time last-value]
      (if (and last-time last-value)
        (let [t (/ (- now last-time) ival)]
          (float (+ (* (- 1 t) last-value) (* t @value))))
        @value)))
  ([value cursor ival]
    (fn [now last-time last-value]
      (if (and last-time last-value)
        (let [t (/ (- now last-time) ival)]
          (float (+ (* (- 1 t) last-value) (* t (get-in @value cursor)))))
        (get-in @value cursor)))))

(defn smootherstep [value ival]
  (fn [now last-time last-value]
    (if (and last-time last-value)
      (let [t (/ (- now last-time) ival)
            t' (* t t t (+ (* t (- (* t 6) 15)) 10))]
        (float (+ (* (- 1 t') last-value) (* t' @value))))
      @value)))

#_(defn spreader [in ival out]
  ;; keep track of count-values, curr and prev values (use sliding buffer?)
  ;; make sure that at end of interval 2, all packets from interval 1 were
  ;; broadcast, using acceleration and deceleration. easily computable.

  ;; use old * (1 - a) + new * a?

  ;; to get pairs, (partition 2 1 s)

  (go-loop []
    (when-let [[c0 c1] (<! pairs)]
      (loop [dt 0
             freq (/ c0 ival)]
        ;; todo check if c0 > 0 and c1 > 0
        (>! out 1)
        (let [delay (/ 1 freq)]
          (<! (timeout delay))
          (recur (+ dt delay)
                 (/ (+ c0 (* (- c1 c0) (interp dt))) ival))))
      (recur)))

  ;; have a channel emit [vold vnew t], keep track of latest vec and use that in (update)

  ;; in update, keep the last read value recurring. try to read the value at the start,
  ;; if it fails just use the last read value.
  ;; this means that these values should be written on a sliding buffer


  ;; for the output, we need to know the frequency at any time (inverting it to get the delay we want)
  ;; the frequency at t0 is count0 / ival
  ;; the frequency at t1 = t0 + ival is count1 / ival
  ;; the frequency at t0 + dt is count0 / ival * (1 - interp(dt)) + count1 / ival * interp(dt)
  ;;                           = (count0 * (1 - interp(dt)) + count1 * interp(dt)) / ival
  ;;                           = (count0 + interp(dt) * -count0 + count1 * interp(dt)) / ival
  ;;                           = (count0 + (count1 - count0) * interp(dt)) / ival

  ;; would be nice to have this function work on any data input, buffering items.
  ;; that is difficult for now since we don't know how large the buffer should be
  ;; could be based on count0...
  )

(comment
  (def sn (wifi.sniffer/sniff))
  (def all (wifi.sniffer/all sn))
  (def ones (pipe all (chan 2 (map (constantly 1)))))
  (def counter (count-values ones 1000)))

(comment
  (def mych (chan))
  (def counter (count-values mych 3000))
  (go
    (>! mych 42)
    (<! (timeout 1000))
    (>! mych 1))
  #_ (close! mych)

  (loop []
    (when-let [c (<!! counter)]
      (println "counter:" c)
      (recur))))

(comment
  (let [in (chan)
        ;out (mytest in)
        ]
    (go
      (>! in "hello")
      (<! (timeout 100))
      (>! in ", ")
      (<! (timeout 100))
      (>! in "world")
      (<! (timeout 100))
      (close! in)
      (println "closed"))
    (let [out (async/pipe in (chan))
          result (async/reduce str "" out)]
      (go
        (<! (timeout 150))
        (close! out))
      (go
        (println "out" (<! result))
        ;(println "output" (<! out))
        ))))
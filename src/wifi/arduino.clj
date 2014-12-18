(ns wifi.arduino
  (:require [serial.core :as serial]
            [clojure.core.async :as async :refer [<! put! chan timeout go-loop go close!]]))

(def recv-keys [:pressure-l
                :pressure-r
                :motor-l
                :motor-r
                :attached-l
                :attached-r])

(defn parse-value [msg]
  (->> (-> msg (.substring 1) (.split ","))
       (map read-string)
       (zipmap recv-keys)))

(defn process [state send values msg]
  (case (first msg)
    \? (do
         ;port.clear() ?
         (println "establishing")
         (put! send "!\n"))
    \! (do
         (println "established")
         (swap! state assoc :established true))
    \V (do
         (println "value" msg)
         (put! values (parse-value msg)))
    (println "unknown message:" msg)))

;; TODO what happens here:
;; we stop reading after a \n, waiting for a next call to listener.
;; so if a line is being read while the next one is sent, the first
;; part of the next line is ignored.
(defn listener [proc in]
  (loop [msg ""]
    (if (> (.available in) 0)
      (let [c (char (.read in))]
        (recur (if (= c \newline)
                 (do (proc msg) "")
                 (str msg c)))))))

(comment
  (let [send (chan)]
    (go-loop []
      (when-let [msg (<! send)]
        (<! (timeout 100))
        (println "send:" msg)
        (recur)))
    (with-open [recv (clojure.java.io/input-stream "test.txt")]
      ((partial listener (partial process (atom {:established true}) send)) recv)
      (close! send))))

(def address "tty.usbmodem1421")

(extend-protocol serial.core/Bytable
  String
  (to-bytes [this] (.getBytes this "ASCII")))

(defn connect [in out stop]
  (let [state (atom {:running true
                     :established false})
        port (serial/open address)
        proc (partial process state in out)]
    (serial/listen port (partial listener proc) false)
    (async/go
      (while (@state :running)
        (let [msg (async/<! in)]
          (println "sending" msg)
          (serial/write port msg))))
    (async/go
      (async/<! stop)
      (swap! state assoc :running false)
      (serial/remove-listener port)
      (serial/close port))))

(comment
  #_"deze"
  (def in (chan))
  (def out (chan (async/sliding-buffer 1)))
  (def stop (chan))
  (connect in out stop)
  (do
    (put! in "50,50\n")
    (<!! (timeout 500))
    (put! in "100,100\n")))


(comment
  (defn indexes-of [e coll] (keep-indexed #(if (= e %2) %1) coll))
  (defn next-in [e cycle] (nth cycle (inc (first (indexes-of e cycle)))))
  (defn create-sensor-channel [] (async/chan (async/sliding-buffer 1)))

  (def in (async/chan))
  (def out (async/chan))
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
    (async/close! out)))

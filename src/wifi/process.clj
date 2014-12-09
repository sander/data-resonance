(ns wifi.process
  (:require [clojure.core.async :as async]
            [clojure.core.async.lab :as async.lab]))

(defrecord PublishedProcess [pub stop])
(defn start
  "Drops output items that have no matching subscriptions."
  [cmd process-fn topic-fn]
  (let [process (.start (ProcessBuilder. cmd))
        ch (-> process
               (.getInputStream)
               (clojure.java.io/reader)
               (line-seq)
               (process-fn)
               (async.lab/spool))
        publication (async/pub ch topic-fn (fn [_] (async/sliding-buffer 1)))]
    (PublishedProcess. publication (fn []
                            (.destroy process)
                            (async/close! ch)))))
(defn stop [sn] ((:stop sn)))

(comment
(defrecord SeqProcess [out stop])
(defn start
  [cmd]
  (let [process (.start (ProcessBuilder. cmd))
        ch (-> process
               (.getInputStream)
               (clojure.java.io/reader)
               (line-seq)
               (async.lab/spool))]
    (SeqProcess. ch (fn [] (.destroy process) (async/close! ch)))))
(def pinger ["ping" "google.com"])
(def res (start pinger))
(async/<!! (:out res))
(async/<!! (:out res))
(async/<!! (:out res))
)

(comment
  (def cmd ["ping" "google.com"])
  (defn process-line [line]
    {:aap :noot})
  (defn process [lines]
    (sequence (map process-line) lines))
  (defn topic [p]
    (:aap p))
  (def pinger (start cmd process topic))
  (def mychan (async/chan))
  (def mysub (async/sub (:pub pinger) :noot mychan))
  (def result (atom nil))
  (+ 1 1)
  (async/go (while (reset! result (async/<! mychan))))
  (deref result)
  @result)

;(def result (async/take! mysub))
;result

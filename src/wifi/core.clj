(ns wifi.core
  (:require [quil.core :as q]
            ;[overtone.core :as overtone]
            [clojure.core.async :as async]
            [clojure.core.async.lab :as async-lab]
            ;[clojure.java.shell :refer [sh]]
            [me.raynes.conch.low-level :as sh]
            [clojure.java.io :refer [copy]])
  ;(:import (net.sourceforge.jpcap.capture PacketCapture PacketListener))
  (:gen-class))

(comment
  (defonce sc-server (overtone/boot-server))
  (def pop-sample (overtone/sample "/System/Library/Sounds/Pop.aiff"))
  (def tink-sample (overtone/sample "/System/Library/Sounds/Tink.aiff")))

(def counter (atom 0))
(def promiscuous true)
(def snaplen 96)
(def timeout 1)

(comment
  (defn shell-handler
    [f proc-desc args]
    (let [process (.start (ProcessBuilder. args))
          input (.getInputStream process)
          error-input (.getErrorStream process)
          output (.getOutputStream process)
          writer (io/writer output)]
      (handle-output (io/reader input)
                     (f (str proc-desc ":out")))
      (handle-output (io/reader error-input)
                     (f (str proc-desc ":err")))
      {:close #(.close output)
       :write (fn [s]
                (.write writer s)
                (.flush writer))})))

(defn sniff []
  (let [process (.start (ProcessBuilder. ["tshark" "-Il" "-s96"]))
        stdout (.getInputStream process)
        reader (clojure.java.io/reader input)
        lines (line-seq reader)
        ch (lab/spool lines)]
    [ch
     (fn []
       (.destroy process)
       (async/close! ch))]))
(comment
  (def process (.start (ProcessBuilder. ["tshark"])))
  (def input (.getInputStream process))
  (def error-input (.getErrorStream process))
  (def output (.getOutputStream process))
  (def myreader (clojure.java.io/reader input))
  (def lines (line-seq myreader)))

(comment
  (def p (sh/proc "tshark"))
  (def out (atom "unset"))
  (def rdr (clojure.java.io/reader (:out p)))
  (async/thread
    (reset! out (line-seq rdr)))
  (with-open [rdr (clojure.java.io/reader (:out p))]
    (reset! out (line-seq rdr))))

(comment
  (def writer (java.io.StringWriter.))
  (programs tshark))

(comment
  (def process
    (.exec (Runtime/getRuntime) "tshark -Il -T pdml -s96"))
  (def process)
  (comment
    (with-open [stdout (.getInputStream process)
                bout (java.io.StringWriter.)]
      (copy stdout bout :encoding (.name (java.nio.charset.Charset/defaultCharset)))
      (.toString bout)))
  (let [stdout (.getInputStream process)
        reader (java.io.BufferedReader. (java.io.InputStreamReader. stdout))]
    (def r reader))
  (.destroy process))

; how to put card in monitoring mode?
; also listen in to dropped packets?

(defn listen []
  (def pcap (PacketCapture.))
  (def device (.findDevice pcap))
  (.open pcap device snaplen promiscuous timeout)
  (.addPacketListener pcap (proxy [PacketListener] []
                             (packetArrived [packet]
                               ;(println "arrived" (.getName (class packet)))
                               (swap! counter inc)
                               ;(pop-sample)
                               )))
  (async/thread
    (.capture pcap -1)
    ;(.endCapture pcap)
    (.close pcap)
    (println "closed")))
;; need control: don't want to wait for 10 packets
;; was a timeout setting in C

;(def minim (Minim. (proxy)))

; print last packet info?
; then: collect bar charts

;; appears that jpacp doesn't do rfmon.
;; easiest would be to run a c program that prints the data to stdout,
;; then parse..., eg tcpdump

(defn sketch []
  (defn setup []
    (q/smooth)
    (q/frame-rate 60)
    (q/background 200))
  (defn draw []
    ;(q/stroke (q/random 255) 0 0)
    ;(q/stroke-weight (q/random 10))
    ;(q/fill 0 0 (q/random 255))
    (q/background 200)
    (q/text (str "test" "\n" @counter) 0 100)

    (let [diam (q/random 100)
          ;      x (q/random (q/width))
          ;y (q/random (q/height))
          ]
      ;(q/ellipse x y diam diam)
      ))

  (q/defsketch wifi-sketch
               :title "Wi-Fi"
               :setup setup
               :draw draw
               :size [323 200]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "hello world"))
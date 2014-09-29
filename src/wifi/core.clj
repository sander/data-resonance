(ns wifi.core
  (:require [quil.core :as q]
            [overtone.core :as overtone])
  (:import (net.sourceforge.jpcap.capture PacketCapture PacketListener))
  (:gen-class))

(defonce sc-server (overtone/boot-server))
(def pop-sample (overtone/sample "/System/Library/Sounds/Pop.aiff"))
(def tink-sample (overtone/sample "/System/Library/Sounds/Tink.aiff"))

(comment
  (def pcap (PacketCapture.))
  (def device (.findDevice pcap))
  (.open pcap device 96 true 1)
  (.addPacketListener pcap (proxy [PacketListener] []
                             (packetArrived [packet]
                               (println "arrived" (.getName (class packet)))
                               (pop-sample))))
  (.capture pcap -1)
  (.endCapture pcap)
  (.close pcap))
;; need control: don't want to wait for 10 packets
;; was a timeout setting in C

;(def minim (Minim. (proxy)))

(comment
  (defn setup []
    (q/smooth)
    (q/frame-rate 1)
    (q/background 200))
  (defn draw []
    (q/stroke (q/random 255) 0 0)
    (q/stroke-weight (q/random 10))
    (q/fill 0 0 (q/random 255))

    (let [diam (q/random 100)
          x (q/random (q/width))
          y (q/random (q/height))]
      (q/ellipse x y diam diam)))

  (q/defsketch wifi-sketch
               :title "Wi-Fi"
               :setup setup
               :draw draw
               :size [323 200]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "hello world"))
(ns wifi.sound
  (:require [overtone.core :as overtone]))

(defn start []
  (defonce sc-server (overtone/boot-server))
  (def pop-sample (overtone/sample "/System/Library/Sounds/Pop.aiff"))
  (def tink-sample (overtone/sample "/System/Library/Sounds/Tink.aiff")))

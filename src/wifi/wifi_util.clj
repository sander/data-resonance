(ns wifi.wifi-util
  (:require [wifi.util :refer [transduce-chan reduce-over-interval]]))

(defn bytes-chan [ch]
  (let [key :data.len]
    (transduce-chan (comp (filter key) (map key)) ch)))
(defn bytes-per-interval [in & args]
  (apply (reduce-over-interval + 0) (bytes-chan in) args))
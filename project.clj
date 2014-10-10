(defproject wifi "0.1.0-SNAPSHOT"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"
            :license {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}
            :dependencies [[org.clojure/clojure "1.7.0-alpha1"]
                           [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                           [org.clojure/data.priority-map "0.0.5"]
                           [quil "2.2.2"]
                           [overtone "0.9.1"]
                           [clj-firmata "2.0.0-SNAPSHOT"]
                           [serial-port "1.1.2"]]
            :main ^:skip-aot wifi.core
            :target-path "target/%s"
            :profiles {:uberjar {:aot :all}})

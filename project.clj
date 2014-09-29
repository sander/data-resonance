(defproject wifi "0.1.0-SNAPSHOT"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"
            :license {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [jpcap "0.1.18-002"]
                           [jpcap/jpcap-macosx-native-deps "0.1.18-002"]
                           [quil "2.2.2"]
                           ;[ddf.minim "2.2.0"]
                           [overtone "0.9.1"]]
            :main ^:skip-aot wifi.core
            :target-path "target/%s"
            :profiles {:uberjar {:aot :all}}
            ;:jvm-opts [~(str "-Djava.library.path=native/:" (System/getenv "LD_LIBRARY_PATH"))]
            )

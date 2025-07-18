(defproject clj-halite "0.1.0-SNAPSHOT"
  :description "Halite Bot Challenge"
  :url "https://github.com/yawnt/parentheses"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.data "0.1.1"]]
                ;  [org.clojure/math.combinatorics "0.1.3"]]
  :main ^:skip-aot halite.core
  :target-path "target/%s"
  :java-source-paths ["src_java"]
  :plugins [[lein-exec "0.3.2"]]

  :profiles {:uberjar {:aot :all
                       :uberjar-name "MyBot.jar"}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]
                                  ; [org.clojars.gjahad/debug-repl "0.3.3"]
                                  [proto-repl "0.3.1"]
                                  [criterium "0.4.4"]]
                   :jvm-opts ^:replace ["-Dcom.sun.management.jmxremote"
                                        "-Dcom.sun.management.jmxremote.ssl=false"
                                        "-Dcom.sun.management.jmxremote.authenticate=false"
                                        "-Dcom.sun.management.jmxremote.port=1098"]
                   :source-paths ["src" "dev" "test"]}})

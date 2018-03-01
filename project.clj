(defproject trada "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.7.0"]
                 [stylefruits/gniazdo "1.0.1-SNAPSHOT"]
                 [cheshire "5.8.0"]
                 [clojure-lanterna "0.9.7"]
                 [spootnik/kinsky "0.1.20"]
                 [org.clojure/tools.trace "0.7.9"]
                 [org.clojure/data.csv "0.1.4"]
                 [incanter "1.5.7"]
                 [hswick/jutsu "0.1.2"]]
  :main trada.core
  :profiles {:dev 
             {:plugins
              [[cider/cider-nrepl "0.17.0-SNAPSHOT"]]}})



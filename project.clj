(defproject com.unbounce/clojure-dogstatsd-client "0.1.0-SNAPSHOT"
  :description "A thin veneer over java-dogstatsd-client"
  :url "https://github.com/unbounce/clojure-dogstatsd-client"
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/MIT"
            :comments "Copyright (c) 2018 Unbounce Marketing Solutions Inc."}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.datadoghq/java-dogstatsd-client "2.5"]]
  :global-vars {*warn-on-reflection* true})
